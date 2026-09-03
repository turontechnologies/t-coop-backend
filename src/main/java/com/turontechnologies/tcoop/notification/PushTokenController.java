package com.turontechnologies.tcoop.notification;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the mobile app registers/unregisters an Expo push token for the signed-in member, so real
 * device notifications (outside the app, not just the in-app bell) can reach them. A token
 * belongs to a device, not a person — see {@link PushToken}'s own doc for why registering an
 * already-known token reassigns it rather than duplicating.
 */
@RestController
public class PushTokenController {

  private final PushTokenRepository pushTokenRepository;

  public PushTokenController(PushTokenRepository pushTokenRepository) {
    this.pushTokenRepository = pushTokenRepository;
  }

  @PostMapping("/api/v1/push-tokens")
  public ResponseEntity<?> register(Authentication authentication, @Valid @RequestBody RegisterPushTokenRequest request) {
    String callerId = (String) authentication.getPrincipal();
    PushToken existing = pushTokenRepository.findByToken(request.token()).orElse(null);
    if (existing != null) {
      existing.setMemberId(callerId);
      existing.setPlatform(request.platform());
      pushTokenRepository.save(existing);
    } else {
      pushTokenRepository.save(new PushToken(callerId, request.token(), request.platform()));
    }
    return ResponseEntity.ok(Map.of("status", "registered"));
  }

  @DeleteMapping("/api/v1/push-tokens")
  public ResponseEntity<?> unregister(Authentication authentication, @Valid @RequestBody UnregisterPushTokenRequest request) {
    String callerId = (String) authentication.getPrincipal();
    pushTokenRepository
        .findByToken(request.token())
        .filter(existing -> existing.getMemberId().equals(callerId))
        .ifPresent(pushTokenRepository::delete);
    return ResponseEntity.ok(Map.of("status", "unregistered"));
  }
}
