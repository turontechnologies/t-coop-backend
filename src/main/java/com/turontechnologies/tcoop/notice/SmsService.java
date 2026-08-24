package com.turontechnologies.tcoop.notice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Real SMS delivery via Termii (https://termii.com) — chosen as the platform's SMS provider for
 * its free trial credit and Nigeria-first fit alongside the existing Paystack/OPay integrations.
 * Reads the API key/sender ID live from {@link PlatformSettings} (Settings -> Integrations),
 * never a static env var — same convention as {@code PaymentGatewayService} for Paystack/
 * Flutterwave verification. If SMS isn't enabled or configured, callers get a clear "not
 * configured" result rather than a confusing failure — see {@code NoticeController}, which treats
 * that the same way it treats a failed send: logged, never blocking the notice/notification
 * itself.
 */
@Service
public class SmsService {

  private static final Logger log = LoggerFactory.getLogger(SmsService.class);
  private static final Integer SETTINGS_SINGLETON_ID = 1;
  private static final String TERMII_SEND_URL = "https://api.ng.termii.com/api/sms/send";

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper objectMapper;
  private final PlatformSettingsRepository settingsRepository;

  public SmsService(ObjectMapper objectMapper, PlatformSettingsRepository settingsRepository) {
    this.objectMapper = objectMapper;
    this.settingsRepository = settingsRepository;
  }

  public record SendResult(boolean success, String message) {}

  /** Normalizes a Nigerian phone number to the digits-only, country-code-prefixed shape Termii
   * expects (e.g. "2348012345678") — handles the common "0801...", "+234801...", and "234801..."
   * input shapes this app's phone fields can hold. */
  static String normalizePhone(String phone) {
    if (phone == null) return null;
    String digits = phone.trim().replaceAll("[^0-9]", "");
    if (digits.startsWith("234")) return digits;
    if (digits.startsWith("0")) return "234" + digits.substring(1);
    return digits;
  }

  public SendResult sendSms(String phone, String message) {
    PlatformSettings settings = settingsRepository.findById(SETTINGS_SINGLETON_ID).orElse(null);
    if (settings == null || !settings.isSmsEnabled()) {
      return new SendResult(false, "SMS isn't enabled for this platform.");
    }
    String apiKey = settings.getSmsApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return new SendResult(false, "SMS isn't configured for this platform.");
    }
    String to = normalizePhone(phone);
    if (to == null || to.isBlank()) {
      return new SendResult(false, "No phone number on file.");
    }
    // Termii's own name is the safe, always-available default for the "generic" channel when no
    // custom Sender ID has been registered in the Termii dashboard yet.
    String senderId =
        settings.getSmsSenderId() == null || settings.getSmsSenderId().isBlank()
            ? "Termii"
            : settings.getSmsSenderId();

    try {
      Map<String, String> payload =
          Map.of(
              "to", to,
              "from", senderId,
              "sms", message,
              "type", "plain",
              "channel", "generic",
              "api_key", apiKey);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(TERMII_SEND_URL))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(15))
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      if (response.statusCode() != 200) {
        String errorMessage = body.path("message").asText("Termii couldn't send that SMS.");
        log.warn("Termii SMS to {} failed ({}): {}", to, response.statusCode(), errorMessage);
        return new SendResult(false, errorMessage);
      }
      return new SendResult(true, body.path("message").asText("Sent"));
    } catch (Exception e) {
      log.warn("Termii SMS to {} failed: {}", to, e.getMessage());
      return new SendResult(false, "Couldn't reach the SMS provider.");
    }
  }
}
