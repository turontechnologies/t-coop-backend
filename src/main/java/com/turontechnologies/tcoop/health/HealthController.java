package com.turontechnologies.tcoop.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple liveness check — useful for confirming the deployed backend is up
 * and reachable from the frontend (Vercel) before wiring up real endpoints.
 */
@RestController
public class HealthController {

  @GetMapping("/api/health")
  public Map<String, Object> health() {
    return Map.of(
        "status", "ok",
        "service", "t-coop-backend",
        "timestamp", Instant.now().toString());
  }
}
