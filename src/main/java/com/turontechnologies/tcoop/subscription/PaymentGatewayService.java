package com.turontechnologies.tcoop.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
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
}
