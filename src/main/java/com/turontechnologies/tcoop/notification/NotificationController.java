package com.turontechnologies.tcoop.notification;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every authenticated caller only ever sees their own notifications — there is no super-admin
 * "view anyone's feed" mode here, unlike the audit log. {@code authentication.getPrincipal()} is
 * always the caller's own member id (see JwtAuthenticationFilter), so scoping is automatic: the
 * repository query is always filtered to that id, never a path parameter.
 */
@RestController
public class NotificationController {

  private static final int MAX_ENTRIES = 50;

  private final NotificationRepository notificationRepository;

  public NotificationController(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @GetMapping("/api/v1/notifications")
  public ResponseEntity<?> list(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    List<NotificationDto> dtos =
        notificationRepository
            .findAllByRecipientMemberIdOrderByCreatedAtDesc(callerId, PageRequest.of(0, MAX_ENTRIES))
            .stream()
            .map(NotificationDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/api/v1/notifications/unread-count")
  public ResponseEntity<?> unreadCount(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    long count = notificationRepository.countByRecipientMemberIdAndReadFalse(callerId);
    return ResponseEntity.ok(Map.of("count", count));
  }

  @PatchMapping("/api/v1/notifications/{id}/read")
  public ResponseEntity<?> markRead(Authentication authentication, @PathVariable Long id) {
    String callerId = (String) authentication.getPrincipal();
    Notification notification = notificationRepository.findById(id).orElse(null);
    if (notification == null || !notification.getRecipientMemberId().equals(callerId)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that notification"));
    }
    notification.markRead();
    notificationRepository.save(notification);
    return ResponseEntity.ok(NotificationDto.from(notification));
  }

  @PatchMapping("/api/v1/notifications/read-all")
  public ResponseEntity<?> markAllRead(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    List<Notification> unread =
        notificationRepository
            .findAllByRecipientMemberIdOrderByCreatedAtDesc(callerId, PageRequest.of(0, MAX_ENTRIES))
            .stream()
            .filter(notification -> !notification.isRead())
            .toList();
    unread.forEach(Notification::markRead);
    notificationRepository.saveAll(unread);
    return ResponseEntity.ok(Map.of("updated", unread.size()));
  }
}
