package com.turontechnologies.tcoop.support;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A member raises an issue to their own co-op's admin; the admin resolves or closes it directly,
 * or escalates it (full history intact) to the super admin. An admin can also raise their own
 * issue straight to the super admin. Access control here checks {@code Member.getRole()} directly
 * (member/admin/super_admin) — deliberately not integrated with the tab-level CoopRole/permission
 * system built elsewhere in this app, matching the simpler model the mock version already shipped.
 */
@RestController
public class SupportTicketController {

  private final SupportTicketRepository ticketRepository;
  private final SupportTicketEventRepository eventRepository;
  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final NotificationService notificationService;
  private final AuditLogService auditLogService;

  public SupportTicketController(
      SupportTicketRepository ticketRepository,
      SupportTicketEventRepository eventRepository,
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      NotificationService notificationService,
      AuditLogService auditLogService) {
    this.ticketRepository = ticketRepository;
    this.eventRepository = eventRepository;
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.notificationService = notificationService;
    this.auditLogService = auditLogService;
  }

  @PostMapping("/api/v1/support/tickets")
  public ResponseEntity<?> raise(
      Authentication authentication, @Valid @RequestBody RaiseTicketRequest request, HttpServletRequest httpRequest) {
    Member caller = currentMember(authentication);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"member".equals(caller.getRole()) && !"admin".equals(caller.getRole())) {
      return ResponseEntity.status(403).body(Map.of("error", "Only members and admins can raise support tickets"));
    }

    // A co-op's admin IS the member row whose id equals the co-op's own id (structural invariant
    // used throughout this app) — so a member's ticket belongs to their own cooperativeId, while
    // an admin's own ticket belongs to their own member id.
    String cooperativeId = "member".equals(caller.getRole()) ? caller.getCooperativeId() : caller.getId();
    String assignedToRole = "member".equals(caller.getRole()) ? "admin" : "super_admin";

    SupportTicket ticket =
        new SupportTicket(
            request.subject(), request.category(), request.description(), cooperativeId, caller.getId(),
            caller.getRole(), assignedToRole);
    ticketRepository.save(ticket);
    eventRepository.save(
        new SupportTicketEvent(
            ticket.getId(), "Raised", caller.getId(), caller.getFullName(), caller.getRole(),
            request.description(), request.attachmentUrl()));

    String link = "/support/" + ticket.getId();
    if ("admin".equals(assignedToRole)) {
      notificationService.notifyCoopAdmin(
          cooperativeId, "SUPPORT_TICKET_RAISED", "New support ticket", request.subject(), link);
    } else {
      notificationService.notifyAllSuperAdmins(
          "SUPPORT_TICKET_RAISED", "New support ticket", request.subject(), link);
    }

    auditLogService.log(caller.getId(), caller.getRole(), "Support", "Create", request.subject(), "Info", httpRequest);
    return ResponseEntity.ok(toDto(ticket));
  }

  /** Member: their own tickets. Admin: every ticket touching their own co-op (raised to them by a
   * member, plus their own tickets to the super admin) — split into tabs client-side. Super admin:
   * every ticket currently assigned to the platform, across every co-op. */
  @GetMapping("/api/v1/support/tickets")
  public ResponseEntity<?> list(Authentication authentication) {
    Member caller = currentMember(authentication);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    List<SupportTicket> tickets;
    if ("member".equals(caller.getRole())) {
      tickets = ticketRepository.findAllByRaisedByIdOrderByCreatedAtDesc(caller.getId());
    } else if ("admin".equals(caller.getRole())) {
      tickets = ticketRepository.findAllByCooperativeIdOrderByCreatedAtDesc(caller.getId());
    } else if ("super_admin".equals(caller.getRole())) {
      tickets = ticketRepository.findAllByAssignedToRoleOrderByCreatedAtDesc("super_admin");
    } else {
      return ResponseEntity.status(403).body(Map.of("error", "You can't view support tickets"));
    }

    List<SupportTicketDto> dtos =
        tickets.stream().map(this::toDto).sorted(Comparator.comparing(SupportTicketDto::createdAt).reversed()).toList();
    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/api/v1/support/tickets/{id}")
  public ResponseEntity<?> get(Authentication authentication, @PathVariable String id) {
    var access = requireView(authentication, id);
    if (access.error() != null) return access.error();
    return ResponseEntity.ok(toDto(access.ticket()));
  }

  @PatchMapping("/api/v1/support/tickets/{id}/reply")
  public ResponseEntity<?> reply(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody ReplyRequest request,
      HttpServletRequest httpRequest) {
    var access = requireParty(authentication, id);
    if (access.error() != null) return access.error();

    SupportTicket ticket = access.ticket();
    if ("Resolved".equals(ticket.getStatus()) || "Closed".equals(ticket.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This ticket is closed — reopen it first."));
    }

    Member caller = access.caller();
    eventRepository.save(
        new SupportTicketEvent(
            ticket.getId(), "Reply", caller.getId(), caller.getFullName(), caller.getRole(),
            request.message(), request.attachmentUrl()));

    String link = "/support/" + ticket.getId();
    if (caller.getId().equals(ticket.getRaisedById())) {
      notifyAssignee(ticket, "SUPPORT_TICKET_REPLY", "New reply on your ticket", ticket.getSubject(), link);
    } else {
      notificationService.notify(
          ticket.getRaisedById(), "SUPPORT_TICKET_REPLY", "New reply on your ticket", ticket.getSubject(), link);
    }

    auditLogService.log(caller.getId(), caller.getRole(), "Support", "Update", ticket.getSubject(), "Info", httpRequest);
    return ResponseEntity.ok(toDto(ticket));
  }

  @PatchMapping("/api/v1/support/tickets/{id}/escalate")
  public ResponseEntity<?> escalate(
      Authentication authentication,
      @PathVariable String id,
      @RequestBody(required = false) EscalateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireAssignee(authentication, id);
    if (access.error() != null) return access.error();

    SupportTicket ticket = access.ticket();
    if (!"admin".equals(ticket.getAssignedToRole())) {
      return ResponseEntity.status(409).body(Map.of("error", "This ticket is already with the super admin"));
    }
    if ("Resolved".equals(ticket.getStatus()) || "Closed".equals(ticket.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This ticket is closed — reopen it first."));
    }

    ticket.setStatus("Escalated");
    ticket.setAssignedToRole("super_admin");
    ticketRepository.save(ticket);

    Member caller = access.caller();
    String note = request == null ? null : request.note();
    eventRepository.save(
        new SupportTicketEvent(ticket.getId(), "Escalated", caller.getId(), caller.getFullName(), caller.getRole(), note, null));

    String link = "/support/" + ticket.getId();
    notificationService.notifyAllSuperAdmins("SUPPORT_TICKET_ESCALATED", "Ticket escalated to you", ticket.getSubject(), link);

    auditLogService.log(
        caller.getId(), caller.getRole(), "Support", "Update", "Escalated — " + ticket.getSubject(), "Warning", httpRequest);
    return ResponseEntity.ok(toDto(ticket));
  }

  @PatchMapping("/api/v1/support/tickets/{id}/resolve")
  public ResponseEntity<?> resolve(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody ResolveRequest request,
      HttpServletRequest httpRequest) {
    var access = requireAssignee(authentication, id);
    if (access.error() != null) return access.error();

    SupportTicket ticket = access.ticket();
    var openCheck = requireOpen(ticket);
    if (openCheck != null) return openCheck;

    return finish(ticket, access.caller(), "Resolved", request.resolutionNote(), "SUPPORT_TICKET_RESOLVED", httpRequest);
  }

  @PatchMapping("/api/v1/support/tickets/{id}/close")
  public ResponseEntity<?> close(
      Authentication authentication,
      @PathVariable String id,
      @RequestBody(required = false) CloseRequest request,
      HttpServletRequest httpRequest) {
    var access = requireAssignee(authentication, id);
    if (access.error() != null) return access.error();

    SupportTicket ticket = access.ticket();
    var openCheck = requireOpen(ticket);
    if (openCheck != null) return openCheck;

    String note = request == null ? null : request.note();
    return finish(ticket, access.caller(), "Closed", note, "SUPPORT_TICKET_CLOSED", httpRequest);
  }

  /** The assignee rechecking a ticket they already resolved or closed — reopens it under the same
   * assignee, no role hand-off. */
  @PatchMapping("/api/v1/support/tickets/{id}/reopen")
  public ResponseEntity<?> reopen(
      Authentication authentication,
      @PathVariable String id,
      @RequestBody(required = false) ReopenRequest request,
      HttpServletRequest httpRequest) {
    var access = requireAssignee(authentication, id);
    if (access.error() != null) return access.error();

    SupportTicket ticket = access.ticket();
    if (!"Resolved".equals(ticket.getStatus()) && !"Closed".equals(ticket.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This ticket isn't closed"));
    }

    ticket.setStatus("Open");
    ticket.setResolutionNote(null);
    ticket.setResolvedAt(null);
    ticketRepository.save(ticket);

    Member caller = access.caller();
    String note = request == null ? null : request.note();
    eventRepository.save(
        new SupportTicketEvent(ticket.getId(), "Reopened", caller.getId(), caller.getFullName(), caller.getRole(), note, null));

    String link = "/support/" + ticket.getId();
    notificationService.notify(
        ticket.getRaisedById(), "SUPPORT_TICKET_REOPENED", "Your ticket was reopened", ticket.getSubject(), link);

    auditLogService.log(
        caller.getId(), caller.getRole(), "Support", "Update", "Reopened — " + ticket.getSubject(), "Info", httpRequest);
    return ResponseEntity.ok(toDto(ticket));
  }

  private ResponseEntity<?> finish(
      SupportTicket ticket,
      Member caller,
      String status,
      String note,
      String notificationType,
      HttpServletRequest httpRequest) {
    ticket.setStatus(status);
    ticket.setResolutionNote(note);
    ticket.setResolvedAt(java.time.LocalDateTime.now());
    ticketRepository.save(ticket);

    eventRepository.save(
        new SupportTicketEvent(ticket.getId(), status, caller.getId(), caller.getFullName(), caller.getRole(), note, null));

    String link = "/support/" + ticket.getId();
    String title = "Resolved".equals(status) ? "Your ticket was resolved" : "Your ticket was closed";
    notificationService.notify(ticket.getRaisedById(), notificationType, title, ticket.getSubject(), link);

    auditLogService.log(
        caller.getId(), caller.getRole(), "Support", "Update", status + " — " + ticket.getSubject(), "Success", httpRequest);
    return ResponseEntity.ok(toDto(ticket));
  }

  private ResponseEntity<?> requireOpen(SupportTicket ticket) {
    if ("Resolved".equals(ticket.getStatus()) || "Closed".equals(ticket.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This ticket is already closed"));
    }
    return null;
  }

  private void notifyAssignee(SupportTicket ticket, String type, String title, String message, String link) {
    if ("admin".equals(ticket.getAssignedToRole())) {
      notificationService.notifyCoopAdmin(ticket.getCooperativeId(), type, title, message, link);
    } else {
      notificationService.notifyAllSuperAdmins(type, title, message, link);
    }
  }

  private SupportTicketDto toDto(SupportTicket ticket) {
    Member raiser = memberRepository.findById(ticket.getRaisedById()).orElse(null);
    Cooperative coop = cooperativeRepository.findById(ticket.getCooperativeId()).orElse(null);
    List<SupportTicketEventDto> timeline =
        eventRepository.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
            .map(SupportTicketEventDto::from)
            .toList();
    return SupportTicketDto.from(
        ticket,
        coop != null ? coop.getName() : "Unknown co-operative",
        raiser != null ? raiser.getFullName() : "Unknown member",
        timeline);
  }

  private Member currentMember(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    return memberRepository.findById(callerId).orElse(null);
  }

  private SupportTicket findTicket(String id) {
    try {
      return ticketRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private record TicketAccess(Member caller, SupportTicket ticket, ResponseEntity<?> error) {}

  private boolean isOwnCoopAdmin(Member caller, SupportTicket ticket) {
    return "admin".equals(caller.getRole()) && caller.getId().equals(ticket.getCooperativeId());
  }

  /** Anyone with legitimate visibility: the raiser, staff of the ticket's own co-op, or a super
   * admin (who sees every platform-assigned ticket, tenant-agnostic by design). */
  private TicketAccess requireView(Authentication authentication, String id) {
    Member caller = currentMember(authentication);
    if (caller == null) {
      return new TicketAccess(null, null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    SupportTicket ticket = findTicket(id);
    if (ticket == null) {
      return new TicketAccess(null, null, ResponseEntity.status(404).body(Map.of("error", "We couldn't find that ticket")));
    }
    boolean canView =
        "super_admin".equals(caller.getRole())
            || caller.getId().equals(ticket.getRaisedById())
            || isOwnCoopAdmin(caller, ticket);
    if (!canView) {
      return new TicketAccess(null, null, ResponseEntity.status(403).body(Map.of("error", "You can't view that ticket")));
    }
    return new TicketAccess(caller, ticket, null);
  }

  /** The raiser or the current assignee — used for replying. */
  private TicketAccess requireParty(Authentication authentication, String id) {
    var access = requireView(authentication, id);
    if (access.error() != null) return access;
    Member caller = access.caller();
    SupportTicket ticket = access.ticket();
    boolean isAssignee =
        ("admin".equals(ticket.getAssignedToRole()) && isOwnCoopAdmin(caller, ticket))
            || ("super_admin".equals(ticket.getAssignedToRole()) && "super_admin".equals(caller.getRole()));
    boolean isRaiser = caller.getId().equals(ticket.getRaisedById());
    if (!isAssignee && !isRaiser) {
      return new TicketAccess(null, null, ResponseEntity.status(403).body(Map.of("error", "You can't reply to that ticket")));
    }
    return access;
  }

  /** Strictly the ticket's current assignee — used for escalate/resolve/close/reopen, where only
   * whoever currently owns the ticket may change its state. */
  private TicketAccess requireAssignee(Authentication authentication, String id) {
    var access = requireView(authentication, id);
    if (access.error() != null) return access;
    Member caller = access.caller();
    SupportTicket ticket = access.ticket();
    boolean isAssignee =
        ("admin".equals(ticket.getAssignedToRole()) && isOwnCoopAdmin(caller, ticket))
            || ("super_admin".equals(ticket.getAssignedToRole()) && "super_admin".equals(caller.getRole()));
    if (!isAssignee) {
      return new TicketAccess(null, null, ResponseEntity.status(403).body(Map.of("error", "Only this ticket's current assignee can do that")));
    }
    return access;
  }
}
