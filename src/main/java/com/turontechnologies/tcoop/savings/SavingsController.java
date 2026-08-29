package com.turontechnologies.tcoop.savings;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Savings oversight — the real backend behind the "Savings & Contributions" tab on a co-op's
 * detail page (Members Savings breakdown, per-type record drill-down, single-record detail), and
 * also what a member's own "My Savings Record" list reads from: a super admin sees any co-op's
 * records, an admin/coop-staff their own co-op's, and a plain member only their own. Read-only by
 * design — the mutations that actually create a savings record (an admin's "Upload Teller", a
 * member's real Paystack "+ New Savings", a withdrawal request/decision) live in
 * {@link SavingsSelfServiceController}.
 */
@RestController
public class SavingsController {

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final AuditLogService auditLogService;

  public SavingsController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SavingsTypeRepository savingsTypeRepository,
      SavingsRecordRepository savingsRecordRepository,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.auditLogService = auditLogService;
  }

  /** The "Members Savings" breakdown table — one row per savings type, with its live total.
   * A super admin sees any co-op's; an admin only their own (this also backs their Savings
   * Settings tab, so it can't stay super-admin-only). */
  @GetMapping("/api/v1/cooperatives/{id}/savings/types")
  public ResponseEntity<?> types(Authentication authentication, @PathVariable String id) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    List<SavingsType> types = savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id);
    List<SavingsRecord> records = savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id);

    Map<UUID, BigDecimal> totalsByType = new HashMap<>();
    for (SavingsRecord record : records) {
      if (!"Success".equals(record.getStatus())) continue;
      totalsByType.merge(record.getSavingsTypeId(), record.getAmount(), BigDecimal::add);
    }

    List<SavingsTypeSummaryDto> dtos =
        types.stream()
            .map(type -> SavingsTypeSummaryDto.from(type, totalsByType.getOrDefault(type.getId(), BigDecimal.ZERO)))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** Admin creates a savings type for their own co-op (or super admin for any) — no seed data,
   * exactly the "never auto-create savings types" rule this codebase has held to from the start. */
  @PostMapping("/api/v1/cooperatives/{id}/savings/types")
  public ResponseEntity<?> createType(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody SavingsTypeCreateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    SavingsType type = new SavingsType(id, request.name(), request.minAmount(), request.maxAmount());
    savingsTypeRepository.save(type);

    auditLogService.log(
        adminIdOf(authentication), access.caller().getRole(), "Savings", "Create", type.getName(), "Success", httpRequest);

    return ResponseEntity.ok(SavingsTypeSummaryDto.from(type, BigDecimal.ZERO));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/savings/types/{typeId}")
  public ResponseEntity<?> updateType(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String typeId,
      @Valid @RequestBody SavingsTypeCreateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    SavingsType type = findType(typeId, id);
    if (type == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings type"));
    }
    type.update(request.name(), request.minAmount(), request.maxAmount());
    savingsTypeRepository.save(type);

    BigDecimal total = totalFor(id, type.getId());
    auditLogService.log(
        adminIdOf(authentication), access.caller().getRole(), "Savings", "Update", type.getName(), "Success", httpRequest);

    return ResponseEntity.ok(SavingsTypeSummaryDto.from(type, total));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/savings/types/{typeId}/status")
  public ResponseEntity<?> updateTypeStatus(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String typeId,
      @Valid @RequestBody SavingsTypeStatusUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    SavingsType type = findType(typeId, id);
    if (type == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings type"));
    }
    type.setStatus(request.status());
    savingsTypeRepository.save(type);

    BigDecimal total = totalFor(id, type.getId());
    auditLogService.log(
        adminIdOf(authentication),
        access.caller().getRole(),
        "Savings",
        "Update",
        type.getName(),
        "Inactive".equals(request.status()) ? "Warning" : "Success",
        httpRequest);

    return ResponseEntity.ok(SavingsTypeSummaryDto.from(type, total));
  }

  private SavingsType findType(String typeId, String cooperativeId) {
    try {
      SavingsType type = savingsTypeRepository.findById(UUID.fromString(typeId)).orElse(null);
      return type != null && cooperativeId.equals(type.getCooperativeId()) ? type : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private BigDecimal totalFor(String cooperativeId, UUID typeId) {
    return savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(cooperativeId).stream()
        .filter(record -> "Success".equals(record.getStatus()) && typeId.equals(record.getSavingsTypeId()))
        .map(SavingsRecord::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String adminIdOf(Authentication authentication) {
    return (String) authentication.getPrincipal();
  }

  /**
   * All of one co-op's savings records, newest first — the per-type drill-down table.
   * Every filter is optional; {@code type} matches a savings type's name (not id), so the
   * frontend's existing name-based route params (e.g. /savings/Basic%20Savings) need no change.
   */
  /** Every co-op-scoped caller (super admin, admin/coop-staff, or a plain member listing their
   * own history) can reach this — a plain member's {@code memberId} filter is always forced to
   * their own id regardless of what's requested, same "self-service can't impersonate" rule as
   * {@link SavingsSelfServiceController}. */
  @GetMapping("/api/v1/cooperatives/{id}/savings")
  public ResponseEntity<?> records(
      Authentication authentication,
      @PathVariable String id,
      @RequestParam(required = false) String memberId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    boolean isStaff =
        "admin".equals(access.caller().getRole())
            || "super_admin".equals(access.caller().getRole())
            || access.caller().getCoopRoleId() != null;
    String effectiveMemberId = isStaff ? memberId : access.caller().getId();

    Map<UUID, String> typeNames = typeNamesFor(id);
    Map<String, String> memberNames = memberNamesFor(id);

    List<SavingsRecordDto> dtos =
        savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(record -> effectiveMemberId == null || effectiveMemberId.equals(record.getMemberId()))
            .filter(record -> type == null || type.equals(typeNames.get(record.getSavingsTypeId())))
            .filter(record -> status == null || status.equals(record.getStatus()))
            .filter(record -> from == null || !record.getRecordDate().isBefore(from))
            .filter(record -> to == null || !record.getRecordDate().isAfter(to))
            .map(
                record ->
                    SavingsRecordDto.from(
                        record,
                        memberNames.getOrDefault(record.getMemberId(), "Unknown member"),
                        typeNames.getOrDefault(record.getSavingsTypeId(), "Unknown type")))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** A single savings transaction's full detail — a plain member may only view their own. */
  @GetMapping("/api/v1/savings/{recordId}")
  public ResponseEntity<?> record(Authentication authentication, @PathVariable String recordId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    SavingsRecord record = findRecord(recordId);
    if (record == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings record"));
    }
    boolean isStaff =
        "admin".equals(caller.getRole())
            || "super_admin".equals(caller.getRole())
            || caller.getCoopRoleId() != null;
    boolean ownsRecord = record.getMemberId().equals(caller.getId());
    boolean staffOwnsCoop = isStaff && record.getCooperativeId().equals(caller.getCooperativeId());
    if (!"super_admin".equals(caller.getRole()) && !ownsRecord && !staffOwnsCoop) {
      return ResponseEntity.status(403).body(Map.of("error", "You can't view that savings record"));
    }

    Member member = memberRepository.findById(record.getMemberId()).orElse(null);
    SavingsType type = savingsTypeRepository.findById(record.getSavingsTypeId()).orElse(null);
    return ResponseEntity.ok(
        SavingsRecordDto.from(
            record,
            member != null ? member.getFullName() : "Unknown member",
            type != null ? type.getName() : "Unknown type"));
  }

  private SavingsRecord findRecord(String id) {
    try {
      return savingsRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Map<UUID, String> typeNamesFor(String cooperativeId) {
    Map<UUID, String> names = new HashMap<>();
    for (SavingsType type : savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId)) {
      names.put(type.getId(), type.getName());
    }
    return names;
  }

  private Map<String, String> memberNamesFor(String cooperativeId) {
    Map<String, String> names = new HashMap<>();
    for (Member member : memberRepository.findAllByCooperativeId(cooperativeId)) {
      names.put(member.getId(), member.getFullName());
    }
    return names;
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can view savings oversight"));
    }
    return null;
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

  /** Any member of this co-op, including a plain member listing/viewing only their own savings
   * history — see {@code records()}/{@code record()}'s own javadoc for how self-service callers
   * get scoped down. */
  private CoopAccess requireMemberOfCoop(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) {
      return new CoopAccess(caller, null);
    }
    if (cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null, ResponseEntity.status(403).body(Map.of("error", "You can only view your own co-operative's savings")));
  }

  /** A member with a coopRoleId (assigned via CoopUserController) gets the same co-op-scoped
   * access as the admin, for their own co-op only — see CooperativeController's requireCoopAccess
   * for the full reasoning. */
  private CoopAccess requireCoopAccess(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) {
      return new CoopAccess(caller, null);
    }
    boolean isCoopStaff = "admin".equals(caller.getRole()) || caller.getCoopRoleId() != null;
    if (isCoopStaff && cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null,
        ResponseEntity.status(403)
            .body(Map.of("error", "You can only manage your own co-operative's savings")));
  }
}
