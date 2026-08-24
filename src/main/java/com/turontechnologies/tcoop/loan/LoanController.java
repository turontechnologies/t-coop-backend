package com.turontechnologies.tcoop.loan;

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
 * Super-admin-only loans oversight — mirrors SavingsController exactly. Read-only: the flows
 * that actually create a loan record (guarantor acceptance, admin approval/disbursement) haven't
 * been cut over from the frontend's mock store to this backend yet — see
 * t-coop-app/documentation/loans-page.md.
 */
@RestController
public class LoanController {

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final AuditLogService auditLogService;

  public LoanController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.auditLogService = auditLogService;
  }

  /** The "Loans" breakdown table — one row per loan type, with its live "Earnings on Loan". A
   * super admin sees any co-op's; an admin only their own (also backs their Loan Settings tab). */
  @GetMapping("/api/v1/cooperatives/{id}/loans/types")
  public ResponseEntity<?> types(Authentication authentication, @PathVariable String id) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    List<LoanType> types = loanTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id);
    List<LoanRecord> records = loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id);

    Map<UUID, BigDecimal> earningsByType = new HashMap<>();
    for (LoanRecord record : records) {
      if ("Rejected".equals(record.getStatus())) continue;
      BigDecimal earned = record.getTotalRepayment().subtract(record.getAmount());
      earningsByType.merge(record.getLoanTypeId(), earned, BigDecimal::add);
    }

    List<LoanTypeSummaryDto> dtos =
        types.stream()
            .map(type -> LoanTypeSummaryDto.from(type, earningsByType.getOrDefault(type.getId(), BigDecimal.ZERO)))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/types")
  public ResponseEntity<?> createType(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody LoanTypeCreateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    LoanType type =
        new LoanType(
            id,
            request.name(),
            request.eligibilityPercent(),
            request.durationMonths(),
            request.maxAmount(),
            request.repaymentInterval(),
            request.numberOfInstallments(),
            request.interestType(),
            request.interestAmount());
    loanTypeRepository.save(type);

    auditLogService.log(
        adminIdOf(authentication), access.caller().getRole(), "Loans", "Create", type.getName(), "Success", httpRequest);

    return ResponseEntity.ok(LoanTypeSummaryDto.from(type, BigDecimal.ZERO));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/loans/types/{typeId}")
  public ResponseEntity<?> updateType(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String typeId,
      @Valid @RequestBody LoanTypeCreateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    LoanType type = findType(typeId, id);
    if (type == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan type"));
    }
    type.update(
        request.name(),
        request.eligibilityPercent(),
        request.durationMonths(),
        request.maxAmount(),
        request.repaymentInterval(),
        request.numberOfInstallments(),
        request.interestType(),
        request.interestAmount());
    loanTypeRepository.save(type);

    BigDecimal earnings = earningsFor(id, type.getId());
    auditLogService.log(
        adminIdOf(authentication), access.caller().getRole(), "Loans", "Update", type.getName(), "Success", httpRequest);

    return ResponseEntity.ok(LoanTypeSummaryDto.from(type, earnings));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/loans/types/{typeId}/status")
  public ResponseEntity<?> updateTypeStatus(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String typeId,
      @Valid @RequestBody LoanTypeStatusUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    LoanType type = findType(typeId, id);
    if (type == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan type"));
    }
    type.setStatus(request.status());
    loanTypeRepository.save(type);

    BigDecimal earnings = earningsFor(id, type.getId());
    auditLogService.log(
        adminIdOf(authentication),
        access.caller().getRole(),
        "Loans",
        "Update",
        type.getName(),
        "Inactive".equals(request.status()) ? "Warning" : "Success",
        httpRequest);

    return ResponseEntity.ok(LoanTypeSummaryDto.from(type, earnings));
  }

  private LoanType findType(String typeId, String cooperativeId) {
    try {
      LoanType type = loanTypeRepository.findById(UUID.fromString(typeId)).orElse(null);
      return type != null && cooperativeId.equals(type.getCooperativeId()) ? type : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private BigDecimal earningsFor(String cooperativeId, UUID typeId) {
    return loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(cooperativeId).stream()
        .filter(record -> !"Rejected".equals(record.getStatus()) && typeId.equals(record.getLoanTypeId()))
        .map(record -> record.getTotalRepayment().subtract(record.getAmount()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String adminIdOf(Authentication authentication) {
    return (String) authentication.getPrincipal();
  }

  /** All of one co-op's loan records, newest first — the per-type drill-down table. Every
   * filter is optional; {@code type} matches a loan type's name (not id). */
  @GetMapping("/api/v1/cooperatives/{id}/loans")
  public ResponseEntity<?> records(
      Authentication authentication,
      @PathVariable String id,
      @RequestParam(required = false) String memberId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    Map<UUID, String> typeNames = typeNamesFor(id);
    Map<String, String> memberNames = memberNamesFor(id);

    List<LoanRecordDto> dtos =
        loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(record -> memberId == null || memberId.equals(record.getMemberId()))
            .filter(record -> type == null || type.equals(typeNames.get(record.getLoanTypeId())))
            .filter(record -> status == null || status.equals(record.getStatus()))
            .filter(record -> from == null || !record.getLoanDate().isBefore(from))
            .filter(record -> to == null || !record.getLoanDate().isAfter(to))
            .map(
                record ->
                    LoanRecordDto.from(
                        record,
                        memberNames.getOrDefault(record.getMemberId(), "Unknown member"),
                        typeNames.getOrDefault(record.getLoanTypeId(), "Unknown type")))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** A single loan's full detail. */
  @GetMapping("/api/v1/loans/{recordId}")
  public ResponseEntity<?> record(Authentication authentication, @PathVariable String recordId) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    LoanRecord record = findRecord(recordId);
    if (record == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan record"));
    }

    Member member = memberRepository.findById(record.getMemberId()).orElse(null);
    LoanType type = loanTypeRepository.findById(record.getLoanTypeId()).orElse(null);
    return ResponseEntity.ok(
        LoanRecordDto.from(
            record,
            member != null ? member.getFullName() : "Unknown member",
            type != null ? type.getName() : "Unknown type"));
  }

  private LoanRecord findRecord(String id) {
    try {
      return loanRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Map<UUID, String> typeNamesFor(String cooperativeId) {
    Map<UUID, String> names = new HashMap<>();
    for (LoanType type : loanTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId)) {
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
          .body(Map.of("error", "Only a super admin can view loans oversight"));
    }
    return null;
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

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
            .body(Map.of("error", "You can only manage your own co-operative's loans")));
  }
}
