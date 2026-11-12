package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
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
 * Loans oversight — mirrors SavingsController exactly, including its access model: a super
 * admin sees any co-op's records, an admin/coop-staff their own co-op's, and a plain member only
 * their own (as applicant or named guarantor). Read-only: the mutations that actually create or
 * progress a loan record live in {@link LoanSelfServiceController}.
 */
@RestController
public class LoanController {

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanGuarantorRepository loanGuarantorRepository;
  private final AuditLogService auditLogService;
  private final CoopPermissions coopPermissions;

  /** Optional so tests can build this controller without it; when absent, lists carry no step
   * titles and committee membership doesn't widen access. */
  private LoanCommittee loanCommittee;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setLoanCommittee(LoanCommittee loanCommittee) {
    this.loanCommittee = loanCommittee;
  }

  private List<LoanCommittee.Step> stepsOf(String cooperativeId) {
    return loanCommittee == null ? List.of() : loanCommittee.stepsOf(cooperativeId);
  }

  public LoanController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanGuarantorRepository loanGuarantorRepository,
      AuditLogService auditLogService,
      CoopPermissions coopPermissions) {
    this.loanGuarantorRepository = loanGuarantorRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.auditLogService = auditLogService;
    this.coopPermissions = coopPermissions;
  }

  /** One row per loan type, for the member-facing application picker and co-op oversight. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/types")
  public ResponseEntity<?> types(Authentication authentication, @PathVariable String id) {
    var access = requireTypeListAccess(authentication, id);
    if (access.error() != null) return access.error();
    String cooperativeId = access.cooperativeId();
    Member caller = access.caller();
    if (coopPermissions.isStaff(caller, cooperativeId)
        && !coopPermissions.allows(caller, cooperativeId, "Loans", null, false)
        && !coopPermissions.allows(caller, cooperativeId, "Settings", "Loan Settings", false)) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Your role doesn't have read access to Loans"));
    }
    if (!cooperativeRepository.existsById(cooperativeId)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    boolean isStaff =
        "super_admin".equals(caller.getRole()) || coopPermissions.isStaff(caller, cooperativeId);
    List<LoanType> types =
        loanTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId);
    List<LoanRecord> records =
        isStaff
            ? loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(cooperativeId)
            : List.of();

    Map<UUID, BigDecimal> earningsByType = new HashMap<>();
    for (LoanRecord record : records) {
      if ("Rejected".equals(record.getStatus())) continue;
      BigDecimal earned = record.getTotalRepayment().subtract(record.getAmount());
      earningsByType.merge(record.getLoanTypeId(), earned, BigDecimal::add);
    }

    List<LoanTypeSummaryDto> dtos =
        types.stream()
            .filter(type -> isStaff || "Active".equals(type.getStatus()))
            .map(
                type ->
                    LoanTypeSummaryDto.from(
                        type,
                        isStaff
                            ? earningsByType.getOrDefault(type.getId(), BigDecimal.ZERO)
                            : BigDecimal.ZERO))
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
    ResponseEntity<?> denied = requireLoanSettingsWrite(access.caller(), id);
    if (denied != null) return denied;
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    LoanType type =
        new LoanType(
            id,
            request.name(),
            request.eligibilityPercentOrDefault(),
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
    ResponseEntity<?> denied = requireLoanSettingsWrite(access.caller(), id);
    if (denied != null) return denied;

    LoanType type = findType(typeId, id);
    if (type == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan type"));
    }
    type.update(
        request.name(),
        request.eligibilityPercentOrDefault(),
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
    ResponseEntity<?> denied = requireLoanSettingsWrite(access.caller(), id);
    if (denied != null) return denied;

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
   * filter is optional; {@code type} matches a loan type's name (not id). Every co-op-scoped
   * caller can reach this — a plain member's {@code memberId} filter is always forced to their
   * own id, same self-service scoping as {@link SavingsController#records}. */
  @GetMapping("/api/v1/cooperatives/{id}/loans")
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
    Member caller = access.caller();
    if (coopPermissions.isStaff(caller, id)) {
      boolean ownRecords = caller.getId().equals(memberId);
      boolean allowed =
          ownRecords
              ? coopPermissions.allows(caller, id, "Loans", "My Loans", false)
              : coopPermissions.allows(caller, id, "Loans", "Requests", false)
                  || coopPermissions.allows(caller, id, "Loans", "Members Loans", false);
      if (!allowed) {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Your role doesn't have read access to these loans"));
      }
    }
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    boolean isStaff =
        "super_admin".equals(caller.getRole()) || coopPermissions.isStaff(caller, id);
    String effectiveMemberId = isStaff ? memberId : caller.getId();
    // A plain member also needs to see loans where they're only the named guarantor, not the
    // applicant — otherwise a guarantor has no way to find the request they were notified about.
    // Only applies to a member's own unfiltered view, not a staff member's memberId query param.
    String guarantorMatchId = isStaff ? null : caller.getId();
    java.util.Set<UUID> guaranteedLoanIds =
        guarantorMatchId == null
            ? java.util.Set.of()
            : loanGuarantorRepository.findAllByMemberId(guarantorMatchId).stream()
                .map(LoanGuarantor::getLoanId)
                .collect(java.util.stream.Collectors.toSet());

    Map<UUID, String> typeNames = typeNamesFor(id);
    Map<String, String> memberNames = memberNamesFor(id);

    List<LoanRecord> matching =
        loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(
                record ->
                    effectiveMemberId == null
                        || effectiveMemberId.equals(record.getMemberId())
                        || (guarantorMatchId != null
                            && (guarantorMatchId.equals(record.getGuarantorId())
                                || guaranteedLoanIds.contains(record.getId()))))
            .filter(record -> type == null || type.equals(typeNames.get(record.getLoanTypeId())))
            .filter(record -> status == null || status.equals(record.getStatus()))
            .filter(record -> from == null || !record.getLoanDate().isBefore(from))
            .filter(record -> to == null || !record.getLoanDate().isAfter(to))
            .toList();
    Map<UUID, List<LoanGuarantor>> guarantorsByLoan =
        loanGuarantorRepository.findAllByLoanIdIn(matching.stream().map(LoanRecord::getId).toList()).stream()
            .collect(java.util.stream.Collectors.groupingBy(LoanGuarantor::getLoanId));
    List<LoanCommittee.Step> steps = stepsOf(id);
    List<LoanRecordDto> dtos =
        matching.stream()
            .map(
                record ->
                    LoanRecordDto.from(
                        record,
                        memberNames.getOrDefault(record.getMemberId(), "Unknown member"),
                        typeNames.getOrDefault(record.getLoanTypeId(), "Unknown type"),
                        guarantorsByLoan.getOrDefault(record.getId(), List.of()),
                        loanCommittee == null ? null : loanCommittee.stepTitle(steps, record)))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** A single loan's full detail — a plain member (applicant or named guarantor) may only view
   * their own. */
  @GetMapping("/api/v1/loans/{recordId}")
  public ResponseEntity<?> record(Authentication authentication, @PathVariable String recordId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    LoanRecord record = findRecord(recordId);
    if (record == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan record"));
    }
    boolean isStaff =
        "super_admin".equals(caller.getRole())
            || coopPermissions.isStaff(caller, record.getCooperativeId());
    boolean isParty =
        record.getMemberId().equals(caller.getId())
            || caller.getId().equals(record.getGuarantorId())
            || loanGuarantorRepository.existsByLoanIdAndMemberId(record.getId(), caller.getId());
    boolean staffHasLoanAccess =
        isStaff
            && (coopPermissions.allows(caller, record.getCooperativeId(), "Loans", "Requests", false)
                || coopPermissions.allows(
                    caller, record.getCooperativeId(), "Loans", "Members Loans", false)
                || (record.getMemberId().equals(caller.getId())
                    && coopPermissions.allows(
                        caller, record.getCooperativeId(), "Loans", "My Loans", false)));
    List<LoanCommittee.Step> steps = stepsOf(record.getCooperativeId());
    boolean onCommittee =
        caller.getCooperativeId() != null
            && caller.getCooperativeId().equals(record.getCooperativeId())
            && steps.stream().anyMatch(s -> s.member().getId().equals(caller.getId()));
    if (!"super_admin".equals(caller.getRole()) && !isParty && !staffHasLoanAccess && !onCommittee) {
      return ResponseEntity.status(403).body(Map.of("error", "You can't view that loan record"));
    }

    Member member = memberRepository.findById(record.getMemberId()).orElse(null);
    LoanType type = loanTypeRepository.findById(record.getLoanTypeId()).orElse(null);
    return ResponseEntity.ok(
        LoanRecordDto.from(
            record,
            member != null ? member.getFullName() : "Unknown member",
            type != null ? type.getName() : "Unknown type",
            loanGuarantorRepository.findAllByLoanIdOrderByCreatedAtAsc(record.getId()),
            loanCommittee == null ? null : loanCommittee.stepTitle(steps, record)));
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

  private ResponseEntity<?> requireLoanSettingsWrite(Member caller, String cooperativeId) {
    if ("super_admin".equals(caller.getRole())
        || coopPermissions.allows(caller, cooperativeId, "Settings", "Loan Settings", true)) {
      return null;
    }
    return ResponseEntity.status(403)
        .body(Map.of("error", "Your role doesn't have write access to Loan Settings"));
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

  private record TypeListAccess(
      Member caller, String cooperativeId, ResponseEntity<?> error) {}

  /** Type pickers are always scoped to the signed-in member's co-op. A plain member cannot
   * choose a tenant by changing the path ID; co-op staff must still name their own co-op, and
   * super admins retain platform-wide read access. */
  private TypeListAccess requireTypeListAccess(
      Authentication authentication, String requestedCooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new TypeListAccess(
          null,
          null,
          ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }

    if ("super_admin".equals(caller.getRole())) {
      return new TypeListAccess(caller, requestedCooperativeId, null);
    }

    String callerCooperativeId = caller.getCooperativeId();
    if (callerCooperativeId == null || callerCooperativeId.isBlank()) {
      return new TypeListAccess(
          null,
          null,
          ResponseEntity.status(403)
              .body(Map.of("error", "Your account isn't associated with a co-operative")));
    }

    boolean isCoopStaff = coopPermissions.isStaff(caller, callerCooperativeId);
    if (isCoopStaff && !requestedCooperativeId.equals(callerCooperativeId)) {
      return new TypeListAccess(
          null,
          null,
          ResponseEntity.status(403)
              .body(Map.of("error", "You can only view your own co-operative's loans")));
    }

    return new TypeListAccess(caller, callerCooperativeId, null);
  }

  /** Any member of this co-op, including a plain member listing/viewing only their own loans —
   * see {@code records()}/{@code record()}'s own javadoc for how self-service callers get
   * scoped down. */
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
        null, ResponseEntity.status(403).body(Map.of("error", "You can only view your own co-operative's loans")));
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
    if (coopPermissions.isStaff(caller, cooperativeId)) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null,
        ResponseEntity.status(403)
            .body(Map.of("error", "You can only manage your own co-operative's loans")));
  }
}
