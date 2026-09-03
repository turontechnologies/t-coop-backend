package com.turontechnologies.tcoop.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fires a real device notification (outside the app, not just the in-app bell) for every push
 * token registered to a member, via Expo's push service — the same one endpoint handles both iOS
 * and Android, since Expo relays to APNs/FCM on our behalf. Always best-effort: the in-app
 * {@link Notification} row this rides alongside is the durable record, so a push failure (device
 * offline, stale token, Expo hiccup) is logged and swallowed, never allowed to fail the caller's
 * real operation.
 */
@Service
public class PushNotificationService {

  private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
  private static final URI EXPO_PUSH_URL = URI.create("https://exp.host/--/api/v2/push/send");

  private final PushTokenRepository pushTokenRepository;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public PushNotificationService(PushTokenRepository pushTokenRepository, ObjectMapper objectMapper) {
    this.pushTokenRepository = pushTokenRepository;
    this.objectMapper = objectMapper;
  }

  public void sendToMember(String memberId, String title, String body, String link) {
    List<PushToken> tokens = pushTokenRepository.findAllByMemberId(memberId);
    if (tokens.isEmpty()) return;

    try {
      List<Map<String, Object>> messages =
          tokens.stream()
              .map(
                  pushToken -> {
                    Map<String, Object> message = new HashMap<>();
                    message.put("to", pushToken.getToken());
                    message.put("title", title);
                    message.put("body", body);
                    message.put("sound", "default");
                    if (link != null) {
                      message.put("data", Map.of("link", link));
                    }
                    return message;
                  })
              .toList();

      String json = objectMapper.writeValueAsString(messages);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(EXPO_PUSH_URL)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(10))
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();

      httpClient
          .sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .whenComplete(
              (response, error) -> {
                if (error != null) {
                  log.warn("Push notification send failed for member {}: {}", memberId, error.getMessage());
                } else if (response.statusCode() >= 300) {
                  log.warn("Push notification send returned {} for member {}: {}", response.statusCode(), memberId, response.body());
                }
              });
    } catch (Exception ex) {
      log.warn("Couldn't build push notification payload for member {}: {}", memberId, ex.getMessage());
    }
  }
}
