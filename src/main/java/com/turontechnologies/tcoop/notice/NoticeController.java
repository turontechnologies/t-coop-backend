package com.turontechnologies.tcoop.notice;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import com.turontechnologies.tcoop.auth.EmailService;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real Notice Board — replaces what used to be a purely local, per-browser Zustand store (see
 * documentation/flows.md). Tenant isolation is structural, not a query-time afterthought: every
 * notice explicitly names the co-op(s) it targets ({@link Notice#targetsCoop}), an admin can only
 * ever name their own co-op no matter what the request body claims, and {@link #isVisible} is the
 * single gate every read/reply/resend/delete goes through.
 */
@RestController
public class NoticeController {

  private static final Logger log = LoggerFactory.getLogger(NoticeController.class);

  private static final int MAX_ENTRIES = 300;
  private static final int EXCERPT_LENGTH = 200;

  private final NoticeRepository noticeRepository;
  private final NoticeReplyRepository noticeReplyRepository;
  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;
  private final EmailService emailService;
  private final SmsService smsService;

  public NoticeController(
      NoticeRepository noticeRepository,
      NoticeReplyRepository noticeReplyRepository,
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      AuditLogService auditLogService,
      NotificationService notificationService,
      EmailService emailService,
      SmsService smsService) {
    this.noticeRepository = noticeRepository;
    this.noticeReplyRepository = noticeReplyRepository;
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.auditLogService = auditLogService;
    this.notificationService = notificationService;
    this.emailService = emailService;
    this.smsService = smsService;
  }

  @GetMapping("/api/v1/notices")
  public ResponseEntity<?> list(Authentication authentication) {
    Member caller = callerOf(authentication);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));

    List<NoticeDto> dtos =
        noticeRepository.findAllByOrderBySendAtDesc(PageRequest.of(0, MAX_ENTRIES)).stream()
            .filter(notice -> isVisible(notice, caller))
            .map(NoticeDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/api/v1/notices/{id}")
  public ResponseEntity<?> get(Authentication authentication, @PathVariable UUID id) {
    Member caller = callerOf(authentication);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));

    Notice notice = noticeRepository.findById(id).orElse(null);
    if (notice == null || !isVisible(notice, caller)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that notice"));
    }
    return ResponseEntity.ok(NoticeDto.from(notice));
  }

  @PostMapping("/api/v1/notices")
  public ResponseEntity<?> create(
      Authentication authentication,
      @Valid @RequestBody NoticeCreateRequest request,
      HttpServletRequest httpRequest) {
    Member caller = callerOf(authentication);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    if (!canManageNotices(caller)) {
      return ResponseEntity.status(403).body(Map.of("error", "Members can't create notices"));
    }
    // A super admin only ever reaches a co-op's admin directly — it's that admin's own call
    // whether/how to pass a platform-wide announcement on to their members, not the platform's.
    // An admin (reaching only their own co-op either way) keeps all three recipient options.
    if ("super_admin".equals(caller.getRole()) && !"All Admins".equals(request.recipient())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "A super admin can only address co-operative admins, not members directly"));
    }

    Set<String> targetCoopIds;
    if ("super_admin".equals(caller.getRole())) {
      for (String coopId : request.targetCoopIds()) {
        if (!cooperativeRepository.existsById(coopId)) {
          return ResponseEntity.status(404)
              .body(Map.of("error", "We couldn't find one of the selected co-operatives"));
        }
      }
      targetCoopIds = new HashSet<>(request.targetCoopIds());
    } else {
      // Never trust the client's targetCoopIds for a non-super-admin — an admin can only ever
      // address their own co-op, whatever the request body says.
      targetCoopIds = Set.of(caller.getCooperativeId());
    }

    LocalDate meetingDate =
        request.meetingDate() == null || request.meetingDate().isBlank()
            ? null
            : LocalDate.parse(request.meetingDate());
    LocalDateTime sendAt = OffsetDateTime.parse(request.sendAt()).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();

    Notice notice =
        new Notice(
            request.type(),
            request.title(),
            request.message(),
            request.recipient(),
            request.medium(),
            meetingDate,
            sendAt,
            caller.getId(),
            caller.getFullName(),
            caller.getRole(),
            targetCoopIds);

    if (request.attachment() != null) {
      notice.setAttachment(
          request.attachment().name(), request.attachment().url(), request.attachment().size());
    }
    noticeRepository.save(notice);

    auditLogService.log(
        caller.getId(), caller.getRole(), "Notices", "Create", notice.getTitle(), "Success", httpRequest);

    // A notice created for right now notifies its audience immediately. One scheduled for later
    // doesn't fire a notification until someone actually resends it (which bumps sendAt to now) —
    // there's no minute-granularity dispatcher watching for a scheduled notice's exact moment to
    // arrive, matching this feature's existing "no active timer" design (see documentation).
    if (notice.isSent()) {
      fanOutNotifications(notice);
    }

    return ResponseEntity.ok(NoticeDto.from(notice));
  }

  @PostMapping("/api/v1/notices/{id}/resend")
  public ResponseEntity<?> resend(
      Authentication authentication, @PathVariable UUID id, HttpServletRequest httpRequest) {
    Member caller = callerOf(authentication);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    if (!canManageNotices(caller)) {
      return ResponseEntity.status(403).body(Map.of("error", "Members can't manage notices"));
    }

    Notice notice = noticeRepository.findById(id).orElse(null);
    if (notice == null || !isVisible(notice, caller)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that notice"));
    }

    notice.resend();
    noticeRepository.save(notice);

    auditLogService.log(
        caller.getId(), caller.getRole(), "Notices", "Update", notice.getTitle(), "Success", httpRequest);

    fanOutNotifications(notice);

    return ResponseEntity.ok(NoticeDto.from(notice));
  }

  @DeleteMapping("/api/v1/notices/{id}")
  public ResponseEntity<?> delete(
      Authentication authentication, @PathVariable UUID id, HttpServletRequest httpRequest) {
    Member caller = callerOf(authentication);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    if (!canManageNotices(caller)) {
      return ResponseEntity.status(403).body(Map.of("error", "Members can't manage notices"));
    }

    Notice notice = noticeRepository.findById(id).orElse(null);
    if (notice == null || !isVisible(notice, caller)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that notice"));
    }

    // notice_targets and notice_replies both cascade-delete at the DB level (V20).
    noticeRepository.delete(notice);

    auditLogService.log(
        caller.getId(), caller.getRole(), "Notices", "Delete", notice.getTitle(), "Warning", httpRequest);

    return ResponseEntity.ok(Map.of("deleted", true));
  }

  @GetMapping("/api/v1/notices/{id}/replies")
  public ResponseEntity<?> replies(Authentication authentication, @PathVariable UUID id) {
    Member caller = callerOf(authentication);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));

    Notice notice = noticeRepository.findById(id).orElse(null);
    if (notice == null || !isVisible(notice, caller)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that notice"));
    }

    List<NoticeReply> replies = noticeReplyRepository.findAllByNoticeIdOrderByCreatedAtAsc(id);
    Map<String, Member> authorsById =
        memberRepository.findAllById(replies.stream().map(NoticeReply::getAuthorId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member));

    List<NoticeReplyDto> dtos =
        replies.stream().map(reply -> NoticeReplyDto.from(reply, authorsById.get(reply.getAuthorId()))).toList();
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/api/v1/notices/{id}/replies")
  public ResponseEntity<?> addReply(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody ReplyCreateRequest request,
      HttpServletRequest httpRequest) {
    Member caller = callerOf(authentication);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));

    Notice notice = noticeRepository.findById(id).orElse(null);
    if (notice == null || !isVisible(notice, caller)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that notice"));
    }

    NoticeReply reply = new NoticeReply(id, caller.getId(), request.message());
    noticeReplyRepository.save(reply);

    auditLogService.log(
        caller.getId(),
        caller.getRole(),
        "Notices",
        "Create",
        "Reply — " + notice.getTitle(),
        "Success",
        httpRequest);

    return ResponseEntity.ok(NoticeReplyDto.from(reply, caller));
  }

  /** A member with a coopRoleId (assigned via CoopUserController, see CooperativeController's
   * requireCoopAccess for the full reasoning) gets the same Notice Board management access as an
   * admin — a plain member (no coopRoleId) still can't create/resend/delete, or see anything
   * beyond what's addressed to them. */
  private boolean canManageNotices(Member caller) {
    return !"member".equals(caller.getRole()) || caller.getCoopRoleId() != null;
  }

  /** Mirrors the frontend's original isNoticeVisibleToRole + noticeTargetsCoop pair, now as the
   * single server-side gate every read/reply/resend/delete goes through — this is what makes
   * tenant isolation real instead of a client-side filter someone could bypass. */
  private boolean isVisible(Notice notice, Member caller) {
    if ("super_admin".equals(caller.getRole())) return true;

    String coopId = caller.getCooperativeId();
    if (coopId == null || !notice.targetsCoop(coopId)) return false;

    if (!notice.isSent()) {
      // A scheduled notice is only visible to whoever manages the co-op, not a plain member.
      return canManageNotices(caller);
    }
    if (!canManageNotices(caller)) {
      return "All Members".equals(notice.getRecipient()) || "All Members & Admins".equals(notice.getRecipient());
    }
    return true;
  }

  /** Notifies every real recipient in-app, and — when the notice's medium calls for it — emails
   * and/or texts them too. Both are best-effort: a delivery failure (or SMS simply not being
   * configured yet in Settings -> Integrations) is logged and never blocks the notice itself or
   * its in-app notification. */
  private void fanOutNotifications(Notice notice) {
    String link = "/notice-board/" + notice.getId();
    String excerpt =
        notice.getMessage().length() > EXCERPT_LENGTH
            ? notice.getMessage().substring(0, EXCERPT_LENGTH).trim() + "…"
            : notice.getMessage();
    boolean sendEmail = notice.getMedium().contains("Email");
    boolean sendSms = notice.getMedium().contains("SMS");

    for (String coopId : notice.getTargetCooperativeIds()) {
      for (Member member : resolveRecipients(coopId, notice.getRecipient())) {
        notificationService.notify(member.getId(), "NOTICE_BOARD", notice.getTitle(), excerpt, link, coopId, null);

        if (sendEmail && member.getEmail() != null && !member.getEmail().isBlank()) {
          try {
            emailService.sendNoticeEmail(
                member.getEmail(), member.getFullName(), notice.getType(), notice.getTitle(), notice.getMessage());
          } catch (EmailDeliveryException e) {
            log.warn(
                "Notice {} created but email to {} failed: {}",
                notice.getId(),
                member.getEmail(),
                e.getMessage());
          }
        }

        if (sendSms && member.getPhone() != null && !member.getPhone().isBlank()) {
          SmsService.SendResult result =
              smsService.sendSms(member.getPhone(), notice.getTitle() + ": " + excerpt);
          if (!result.success()) {
            log.warn(
                "Notice {} created but SMS to {} failed: {}",
                notice.getId(),
                member.getPhone(),
                result.message());
          }
        }
      }
    }
  }

  /** Real member rows a notice's `recipient` setting actually reaches for one co-op — "All
   * Admins" is just that co-op's single admin (its member row whose id equals the co-op's own
   * id), "All Members" excludes it, "All Members & Admins" is everyone. */
  private List<Member> resolveRecipients(String cooperativeId, String recipient) {
    return switch (recipient) {
      case "All Admins" -> {
        Member admin = memberRepository.findById(cooperativeId).orElse(null);
        yield admin == null ? List.of() : List.of(admin);
      }
      case "All Members" ->
          memberRepository.findAllByCooperativeId(cooperativeId).stream()
              .filter(member -> "member".equals(member.getRole()))
              .toList();
      default -> memberRepository.findAllByCooperativeId(cooperativeId);
    };
  }

  private Member callerOf(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    return memberRepository.findById(callerId).orElse(null);
  }
}
