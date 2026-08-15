package com.turontechnologies.tcoop.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Best-effort IP -> approximate location, same free/keyless API the frontend already uses
 * client-side for locally-generated log entries (ipwho.is) — see t-coop-app's
 * src/lib/ip-location.ts. Only called from the audit-log GET endpoint (an infrequent,
 * super-admin-only read), never from the write path (login/logout/profile update), so a slow or
 * failed geo lookup never adds latency to a real user action. Results are cached in memory for
 * the life of the process — a handful of unique IPs in practice, so no eviction policy needed.
 */
@Service
public class LocationResolver {

  private static final Logger log = LoggerFactory.getLogger(LocationResolver.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final Map<String, String> cache = new ConcurrentHashMap<>();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public String resolve(String ip) {
    if (ip == null || ip.isBlank() || isPrivateOrLoopback(ip)) {
      return "Unknown";
    }
    return cache.computeIfAbsent(ip, this::lookup);
  }

  private String lookup(String ip) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create("https://ipwho.is/" + ip))
              .timeout(TIMEOUT)
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());
      if (!body.path("success").asBoolean(false)) {
        return "Unknown";
      }
      String city = body.path("city").asText("");
      String region = body.path("region").asText("");
      String country = body.path("country").asText("");
      String joined =
          java.util.stream.Stream.of(city, region, country)
              .filter(part -> part != null && !part.isBlank())
              .reduce((a, b) -> a + ", " + b)
              .orElse("");
      return joined.isBlank() ? "Unknown" : joined;
    } catch (Exception e) {
      log.warn("Location lookup failed for {}: {}", ip, e.getMessage());
      return "Unknown";
    }
  }

  private boolean isPrivateOrLoopback(String ip) {
    return ip.equals("127.0.0.1")
        || ip.equals("0:0:0:0:0:0:0:1")
        || ip.equals("::1")
        || ip.startsWith("10.")
        || ip.startsWith("192.168.")
        || ip.startsWith("172.17.")
        || ip.startsWith("172.18.")
        || ip.startsWith("172.19.");
  }
}
