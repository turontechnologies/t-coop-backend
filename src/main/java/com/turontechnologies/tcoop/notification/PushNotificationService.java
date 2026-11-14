package com.turontechnologies.tcoop.notification;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Sends a real device notification (outside the app, not just the in-app bell) to every device a
 * member has registered, through Expo's push service, which relays to FCM (Android) and APNs (iOS).
 *
 * <ul>
 *   <li>High priority, with a 7-day expiry: a phone that's offline gets it the moment it comes back
 *       online, and Android shows it as a heads-up banner.
 *   <li>An Android channel per kind of notification (messages, activity, alerts), each set up by the
 *       app with sound and vibration.
 *   <li>The iOS badge is the member's unread count, so the app icon stays accurate.
 *   <li>The notification's id, type and link travel in {@code data}, so opening it marks it read and
 *       goes to the right screen, even from a cold start.
 *   <li>Tokens Expo reports as no longer registered (app uninstalled, token rotated) are deleted.
 * </ul>
 *
 * Always best-effort: the in-app {@link Notification} row is the durable record, so a push failure is
 * logged and never fails the operation that triggered it.
 */
@Service
public class PushNotificationService {

  private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
  private static final URI EXPO_PUSH_URL = URI.create("https://exp.host/--/api/v2/push/send");
  /** How long Expo/FCM/APNs keep trying to deliver to an offline phone. */
  private static final int TTL_SECONDS = 7 * 24 * 60 * 60;

  private final PushTokenRepository pushTokenRepository;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public PushNotificationService(PushTokenRepository pushTokenRepository, ObjectMapper objectMapper) {
    this.pushTokenRepository = pushTokenRepository;
    this.objectMapper = objectMapper;
  }

  /** Which Android channel a notification type belongs to (the app creates these three). */
  static String channelFor(String type) {
    if (type == null) return "activity";
    if (type.startsWith("SUPPORT_TICKET")) return "messages";
    if (type.startsWith("NOTICE") || type.startsWith("SUBSCRIPTION") || type.startsWith("COOPERATIVE")) return "alerts";
    return "activity";
  }

  /** Kept for callers that only have the basics. */
  public void sendToMember(String memberId, String title, String body, String link) {
    sendToMember(memberId, null, null, title, body, link, null);
  }

  public void sendToMember(
      String memberId, Long notificationId, String type, String title, String body, String link, Long badge) {
    List<PushToken> tokens = pushTokenRepository.findAllByMemberId(memberId);
    if (tokens.isEmpty()) return;

    try {
      Map<String, Object> data = new HashMap<>();
      if (link != null) data.put("link", link);
      if (notificationId != null) data.put("notificationId", notificationId);
      if (type != null) data.put("type", type);

      List<Map<String, Object>> messages =
          tokens.stream()
              .map(
                  pushToken -> {
                    Map<String, Object> message = new HashMap<>();
                    message.put("to", pushToken.getToken());
                    message.put("title", title);
                    message.put("body", body);
                    message.put("sound", "default");
                    message.put("priority", "high");
                    message.put("ttl", TTL_SECONDS);
                    message.put("channelId", channelFor(type));
                    if (badge != null) message.put("badge", badge);
                    if (!data.isEmpty()) message.put("data", data);
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
                  log.warn("Push send failed for member {}: {}", memberId, error.getMessage());
                } else if (response.statusCode() >= 300) {
                  log.warn("Push send returned {} for member {}: {}", response.statusCode(), memberId, response.body());
                } else {
                  removeDeadTokens(tokens, response.body());
                }
              });
    } catch (Exception ex) {
      log.warn("Couldn't build push payload for member {}: {}", memberId, ex.getMessage());
    }
  }

  /**
   * Expo answers with one ticket per message, in order. A ticket with error "DeviceNotRegistered"
   * means that token will never work again, so it's deleted.
   */
  void removeDeadTokens(List<PushToken> tokens, String responseBody) {
    try {
      JsonNode tickets = objectMapper.readTree(responseBody).path("data");
      if (!tickets.isArray()) return;
      for (int i = 0; i < tickets.size() && i < tokens.size(); i++) {
        JsonNode ticket = tickets.get(i);
        if ("error".equals(ticket.path("status").asText())
            && "DeviceNotRegistered".equals(ticket.path("details").path("error").asText())) {
          pushTokenRepository.delete(tokens.get(i));
          log.info("Removed a push token that is no longer registered");
        }
      }
    } catch (Exception ex) {
      log.debug("Couldn't read push tickets: {}", ex.getMessage());
    }
  }
}
