package com.turontechnologies.tcoop.notification;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  List<Notification> findAllByRecipientMemberIdOrderByCreatedAtDesc(
      String recipientMemberId, Pageable pageable);

  long countByRecipientMemberIdAndReadFalse(String recipientMemberId);

  boolean existsByRecipientMemberIdAndTypeAndRelatedCooperativeIdAndRelatedExpiresAt(
      String recipientMemberId, String type, String relatedCooperativeId, LocalDate relatedExpiresAt);
}
