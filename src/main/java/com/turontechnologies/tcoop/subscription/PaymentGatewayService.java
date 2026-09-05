package com.turontechnologies.tcoop.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Verifies a subscription payment directly against the real gateway API, server-side, using the
 * secret key the super admin entered in Settings -> Integrations ({@link
 * com.turontechnologies.tcoop.settings.PlatformSettings}) — never a static env var, and never the
 * client's own say-so. This is the one place in the codebase that talks to Paystack/Flutterwave
 * for an inbound (member-pays-platform) charge; the existing {@code src/app/api/paystack/*}
 * route handlers in the frontend are unrelated — those are outbound payouts to member bank
 * accounts and don't verify anything.
 */
@Service
public class PaymentGatewayService {

  private static final Logger log = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper objectMapper;

  public PaymentGatewayService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public record VerificationResult(boolean success, BigDecimal amountPaid, String message) {}

  /** Paystack reports amounts in kobo (amount / 100 = Naira). */
  public VerificationResult verifyPaystack(String reference, String secretKey) {
    if (secretKey == null || secretKey.isBlank()) {
      return new VerificationResult(false, null, "Paystack isn't configured for this platform.");
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://api.paystack.co/transaction/verify/" + reference))
              .header("Authorization", "Bearer " + secretKey)
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      if (response.statusCode() != 200 || !body.path("status").asBoolean(false)) {
        return new VerificationResult(
            false, null, body.path("message").asText("Paystack couldn't verify that payment."));
      }

      JsonNode data = body.path("data");
      String gatewayStatus = data.path("status").asText("");
      if (!"success".equals(gatewayStatus)) {
        return new VerificationResult(false, null, "That payment was not successful (" + gatewayStatus + ").");
      }

      BigDecimal amountKobo = data.path("amount").decimalValue();
      BigDecimal amountNaira = amountKobo.divide(BigDecimal.valueOf(100));
      return new VerificationResult(true, amountNaira, null);
    } catch (HttpTimeoutException e) {
      log.warn("Paystack verify timed out for reference {}", reference);
      return new VerificationResult(false, null, "Couldn't reach Paystack right now. Please try again.");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("Paystack verify failed for reference {}: {}", reference, e.getMessage());
      return new VerificationResult(false, null, "Couldn't verify that payment. Please try again.");
    }
  }

  /** Flutterwave reports amounts in the transaction's own currency's base unit (Naira, not kobo). */
  public VerificationResult verifyFlutterwave(String reference, String secretKey) {
    if (secretKey == null || secretKey.isBlank()) {
      return new VerificationResult(false, null, "Flutterwave isn't configured for this platform.");
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      "https://api.flutterwave.com/v3/transactions/verify_by_reference?tx_ref="
                          + reference))
              .header("Authorization", "Bearer " + secretKey)
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      if (response.statusCode() != 200 || !"success".equals(body.path("status").asText(""))) {
        return new VerificationResult(
            false, null, body.path("message").asText("Flutterwave couldn't verify that payment."));
      }

      JsonNode data = body.path("data");
      String gatewayStatus = data.path("status").asText("");
      if (!"successful".equals(gatewayStatus)) {
        return new VerificationResult(false, null, "That payment was not successful (" + gatewayStatus + ").");
      }

      BigDecimal amountNaira = data.path("amount").decimalValue();
      return new VerificationResult(true, amountNaira, null);
    } catch (HttpTimeoutException e) {
      log.warn("Flutterwave verify timed out for reference {}", reference);
      return new VerificationResult(false, null, "Couldn't reach Flutterwave right now. Please try again.");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("Flutterwave verify failed for reference {}: {}", reference, e.getMessage());
      return new VerificationResult(false, null, "Couldn't verify that payment. Please try again.");
    }
  }

  public record OpayCheckoutResult(
      boolean success, String cashierUrl, String orderNo, String message) {}

  // Deliberately OPay's SANDBOX host, not live — neither real merchant account available to this
  // platform has finished OPay's live verification yet (both show "Test Mode, unverified" on
  // their dashboards; live calls fail with an undocumented "merchant is null"). Switching to
  // live later means: (1) point this at the correct per-country live host — empirically,
  // liveapi.opaycheckout.com for a Nigeria-registered merchant, api.opaycheckout.com for an
  // Egypt-registered one, undocumented anywhere and only discoverable by trial — and (2) confirm
  // the live host expects the same auth split described below (unconfirmed either way, since
  // "merchant is null" was never gotten past on live to check).
  private static final String OPAY_BASE_URL = "https://testapi.opaycheckout.com/api/v1/international";

  /**
   * OPay's checkout is server-initiated (unlike Paystack/Flutterwave's client-side inline
   * widgets): the merchant backend calls cashier/create and redirects the payer to the
   * returned hosted cashierUrl.
   *
   * <p>OPay's two endpoints use two DIFFERENT auth schemes, confirmed empirically against the
   * sandbox (contradicts documentation.opaycheckout.com/api-signature, which describes only the
   * HMAC scheme): {@code cashier/create} takes the raw public key as the bearer token —
   * signing it produces a real, documented "Authentication failed"; {@code cashier/status} (see
   * {@link #verifyOpay}) requires the HMAC-SHA512-over-sorted-JSON scheme that page describes.
   *
   * <p>country/currency are hardcoded to Nigeria (NG/NGN) to match this platform's own pricing
   * (subscription plans are priced in Naira by the super admin) and the configured OPay merchant
   * account, which is Nigeria-registered.
   */
  public OpayCheckoutResult createOpayCheckout(
      String reference,
      BigDecimal amount,
      String returnUrl,
      String productName,
      String productDescription,
      String payerEmail,
      String publicKey,
      String secretKey,
      String merchantId) {
    if (publicKey == null
        || publicKey.isBlank()
        || secretKey == null
        || secretKey.isBlank()
        || merchantId == null
        || merchantId.isBlank()) {
      return new OpayCheckoutResult(false, null, null, "OPay isn't configured for this platform.");
    }
    try {
      Map<String, Object> amountNode = new TreeMap<>();
      amountNode.put("total", amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact());
      amountNode.put("currency", "NGN");

      Map<String, Object> product = new TreeMap<>();
      product.put("name", productName);
      product.put("description", productDescription);

      Map<String, Object> userInfo = new TreeMap<>();
      if (payerEmail != null && !payerEmail.isBlank()) {
        userInfo.put("userEmail", payerEmail);
      }

      Map<String, Object> payload = new TreeMap<>();
      payload.put("reference", reference);
      payload.put("country", "NG");
      payload.put("amount", amountNode);
      payload.put("returnUrl", returnUrl);
      // No webhook receiver is implemented on this backend yet — a real callback isn't needed
      // since confirmation is driven by the payer's own return redirect (see
      // SubscriptionController.confirmPayment), but OPay's cashier/create rejects the request
      // outright without one configured somewhere.
      payload.put("callbackUrl", returnUrl);
      payload.put("product", product);
      if (!userInfo.isEmpty()) payload.put("userInfo", userInfo);

      String json = objectMapper.writeValueAsString(payload);
      JsonNode body = callOpay("/cashier/create", json, "Bearer " + publicKey, merchantId);
      if (body == null) {
        return new OpayCheckoutResult(false, null, null, "Couldn't reach OPay right now. Please try again.");
      }
      if (!"00000".equals(body.path("code").asText(""))) {
        return new OpayCheckoutResult(
            false, null, null, body.path("message").asText("OPay couldn't start that payment."));
      }
      JsonNode data = body.path("data");
      return new OpayCheckoutResult(true, data.path("cashierUrl").asText(null), data.path("orderNo").asText(null), null);
    } catch (Exception e) {
      log.error("OPay checkout create failed for reference {}: {}", reference, e.getMessage());
      return new OpayCheckoutResult(false, null, null, "Couldn't start that payment. Please try again.");
    }
  }

  /** OPay reports amounts in cent units (amount.total / 100). Uses the HMAC-SHA512-signed
   * secret-key auth scheme — see createOpayCheckout's note on why this differs from create. */
  public VerificationResult verifyOpay(String reference, String secretKey, String merchantId) {
    if (secretKey == null || secretKey.isBlank() || merchantId == null || merchantId.isBlank()) {
      return new VerificationResult(false, null, "OPay isn't configured for this platform.");
    }
    try {
      Map<String, Object> payload = new TreeMap<>();
      payload.put("reference", reference);
      payload.put("country", "NG");

      String json = objectMapper.writeValueAsString(payload);
      JsonNode body = callOpay("/cashier/status", json, "Bearer " + hmacSha512Hex(json, secretKey), merchantId);
      if (body == null) {
        return new VerificationResult(false, null, "Couldn't reach OPay right now. Please try again.");
      }
      if (!"00000".equals(body.path("code").asText(""))) {
        return new VerificationResult(false, null, body.path("message").asText("OPay couldn't verify that payment."));
      }

      JsonNode data = body.path("data");
      String gatewayStatus = data.path("status").asText("");
      if (!"SUCCESS".equals(gatewayStatus)) {
        return new VerificationResult(false, null, "That payment was not successful (" + gatewayStatus + ").");
      }

      BigDecimal amountCents = data.path("amount").path("total").decimalValue();
      BigDecimal amountNaira = amountCents.divide(BigDecimal.valueOf(100));
      return new VerificationResult(true, amountNaira, null);
    } catch (HttpTimeoutException e) {
      log.warn("OPay verify timed out for reference {}", reference);
      return new VerificationResult(false, null, "Couldn't reach OPay right now. Please try again.");
    } catch (Exception e) {
      log.error("OPay verify failed for reference {}: {}", reference, e.getMessage());
      return new VerificationResult(false, null, "Couldn't verify that payment. Please try again.");
    }
  }

  public record BankOption(String name, String code) {}

  public record BankListResult(boolean success, List<BankOption> banks, String message) {}

  /** Real, transfer-capable Nigerian banks via Paystack — same list web's bank picker uses,
   * fetched with the platform's own configured Paystack key rather than a static env var, so any
   * platform that's set up Paystack in Settings -> Integrations gets it, mobile included. */
  public BankListResult listPaystackBanks(String secretKey) {
    if (secretKey == null || secretKey.isBlank()) {
      return new BankListResult(false, null, "Paystack isn't configured for this platform.");
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://api.paystack.co/bank?currency=NGN"))
              .header("Authorization", "Bearer " + secretKey)
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      if (response.statusCode() != 200 || !body.path("status").asBoolean(false)) {
        return new BankListResult(false, null, body.path("message").asText("Couldn't load the bank list."));
      }

      List<BankOption> banks = new ArrayList<>();
      for (JsonNode bank : body.path("data")) {
        if (bank.path("active").asBoolean(false)
            && !bank.path("is_deleted").asBoolean(true)
            && bank.path("supports_transfer").asBoolean(false)) {
          banks.add(new BankOption(bank.path("name").asText(), bank.path("code").asText()));
        }
      }
      banks.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
      return new BankListResult(true, banks, null);
    } catch (HttpTimeoutException e) {
      return new BankListResult(false, null, "Couldn't reach Paystack right now. Please try again.");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("Paystack bank list failed: {}", e.getMessage());
      return new BankListResult(false, null, "Couldn't load the bank list. Please try again.");
    }
  }

  public record BankResolveResult(boolean success, String accountName, String message) {}

  /** Resolves a real Nigerian bank account number to its registered name via Paystack. */
  public BankResolveResult resolvePaystackBankAccount(String accountNumber, String bankCode, String secretKey) {
    if (secretKey == null || secretKey.isBlank()) {
      return new BankResolveResult(false, null, "Paystack isn't configured for this platform.");
    }
    try {
      String url =
          "https://api.paystack.co/bank/resolve?account_number="
              + java.net.URLEncoder.encode(accountNumber, StandardCharsets.UTF_8)
              + "&bank_code="
              + java.net.URLEncoder.encode(bankCode, StandardCharsets.UTF_8);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Authorization", "Bearer " + secretKey)
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      if (response.statusCode() != 200 || !body.path("status").asBoolean(false)) {
        return new BankResolveResult(false, null, body.path("message").asText("Couldn't verify that account number."));
      }
      return new BankResolveResult(true, body.path("data").path("account_name").asText(), null);
    } catch (HttpTimeoutException e) {
      return new BankResolveResult(false, null, "Couldn't reach Paystack right now. Please try again.");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("Paystack bank resolve failed: {}", e.getMessage());
      return new BankResolveResult(false, null, "Couldn't verify that account number. Please try again.");
    }
  }

  private JsonNode callOpay(String path, String json, String authorization, String merchantId)
      throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(OPAY_BASE_URL + path))
            .header("Authorization", authorization)
            .header("MerchantId", merchantId)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return objectMapper.readTree(response.body());
  }

  private static String hmacSha512Hex(String data, String secretKey)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }
}
