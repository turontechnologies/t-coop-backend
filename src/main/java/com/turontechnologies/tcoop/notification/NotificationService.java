package com.turontechnologies.tcoop.notification;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The single place any module reaches to notify someone. Every call ends up as one row per
 * recipient — see the {@code notifications} table comment (V19) for why fan-out happens here
 * rather than a shared broadcast row: it keeps tenant isolation structural. A co-op's admin and
 * members only ever receive rows this service explicitly addressed to their own member id; the
 * only way a message reaches more than one co-op is a caller (Notice Board's creation flow, today
 * the only one) explicitly looping this service once per target co-op.
 */
@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final MemberRepository memberRepository;

  public NotificationService(
      NotificationRepository notificationRepository, MemberRepository memberRepository) {
    this.notificationRepository = notificationRepository;
    this.memberRepository = memberRepository;
  }

  /** The general-purpose entry point every convenience method below funnels through. */
  public void notify(
      String recipientMemberId,
      String type,
      String title,
      String message,
      String link,
      String relatedCooperativeId,
      LocalDate relatedExpiresAt) {
    notificationRepository.save(
        new Notification(
            recipientMemberId, type, title, message, link, relatedCooperativeId, relatedExpiresAt));
  }

  public void notify(String recipientMemberId, String type, String title, String message, String link) {
    notify(recipientMemberId, type, title, message, link, null, null);
  }

  /** A co-op's admin IS the member row whose id equals the co-op's own id. */
  public void notifyCoopAdmin(String cooperativeId, String type, String title, String message, String link) {
    notify(cooperativeId, type, title, message, link, cooperativeId, null);
  }

  /** Same as {@link #notifyCoopAdmin} but carries the expiry-dedup context the reminder job needs. */
  public void notifyCoopAdminAboutExpiry(
      String cooperativeId, String type, String title, String message, String link, LocalDate expiresAt) {
    notify(cooperativeId, type, title, message, link, cooperativeId, expiresAt);
  }

  public boolean alreadyNotifiedForExpiry(String cooperativeId, String type, LocalDate expiresAt) {
    return notificationRepository
        .existsByRecipientMemberIdAndTypeAndRelatedCooperativeIdAndRelatedExpiresAt(
            cooperativeId, type, cooperativeId, expiresAt);
  }

  public void notifyAllSuperAdmins(String type, String title, String message, String link) {
    List<Member> superAdmins = memberRepository.findAllByRoleOrderByInvitedAtDesc("super_admin");
    for (Member superAdmin : superAdmins) {
      notify(superAdmin.getId(), type, title, message, link);
    }
  }
}
