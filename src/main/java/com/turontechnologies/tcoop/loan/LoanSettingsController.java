package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings -> Loans: the co-op's loan committee (who approves, in what order), its loan rules
 * (minimum amount, eligibility, guarantors) and how dividends are worked out. Anyone in the co-op
 * can read them (the loan form needs the rules); only the admin, or a role with Settings write,
 * can change them.
 */
@RestController
public class LoanSettingsController {

  public record CommitteeSeat(
      @NotBlank(message = "Pick a member") String memberId,
      @NotBlank(message = "Give each approver a title") @Size(max = 60) String title) {}

  public record LoanSettingsRequest(
      @NotNull @DecimalMin(value = "0", message = "The minimum loan can't be negative") BigDecimal minAmount,
      @NotNull @DecimalMin(value = "0", message = "The application fee can't be negative") BigDecimal applicationFee,
      @NotNull
          @DecimalMin(value = "0.1", message = "The savings multiple must be at least 0.1")
          @DecimalMax(value = "20", message = "The savings multiple can't be more than 20")
          BigDecimal savingsMultiplier,
      @NotNull
          @DecimalMin(value = "1", message = "Within must allow at least 1% of savings")
          @DecimalMax(value = "100", message = "Within can't be more than 100% of savings")
          BigDecimal withinPercent,
      @NotNull @Min(0) @Max(120) Integer minMembershipMonths,
      @NotNull @DecimalMin("0") BigDecimal minSavings,
      @NotNull Boolean allowConcurrentLoans,
      @NotNull @Min(0) @Max(10) Integer minGuarantors,
      @NotBlank @Pattern(regexp = "SavingsPercent|InterestShare|None") String dividendMethod,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal dividendRate,
      @NotBlank @Pattern(regexp = "Monthly|Quarterly|Annually") String dividendFrequency,
      @NotNull @Size(max = 20, message = "A committee can have at most 20 people") List<@Valid CommitteeSeat> committee) {}

  public record CommitteeSeatDto(int position, String memberId, String memberName, String title) {}

  public record LoanSettingsDto(
      BigDecimal minAmount,
      BigDecimal applicationFee,
      BigDecimal savingsMultiplier,
      BigDecimal withinPercent,
      int minMembershipMonths,
      BigDecimal minSavings,
      boolean allowConcurrentLoans,
      int minGuarantors,
      String dividendMethod,
      BigDecimal dividendRate,
      String dividendFrequency,
      List<CommitteeSeatDto> committee,
      /** True when nobody is on the committee, so the co-op admin approves every loan. */
      boolean adminApprovesAlone,
      boolean canEdit) {}

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanCommitteeMemberRepository committeeRepository;
  private final CoopPermissions coopPermissions;
  private final AuditLogService auditLogService;

  public LoanSettingsController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanCommitteeMemberRepository committeeRepository,
      CoopPermissions coopPermissions,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.committeeRepository = committeeRepository;
    this.coopPermissions = coopPermissions;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/cooperatives/{id}/loan-settings")
  public ResponseEntity<?> get(Authentication authentication, @PathVariable String id) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    if (!"super_admin".equals(caller.getRole()) && !id.equals(caller.getCooperativeId())) {
      return ResponseEntity.status(403).body(Map.of("error", "You can only see your own co-operative's settings"));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    return ResponseEntity.ok(dtoOf(coop, canEdit(caller, id)));
  }

  @PutMapping("/api/v1/cooperatives/{id}/loan-settings")
  @Transactional
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody LoanSettingsRequest request,
      HttpServletRequest httpRequest) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    if (!canEdit(caller, id)) {
      return ResponseEntity.status(403).body(Map.of("error", "Only the co-operative's admin can change loan settings"));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));

    Set<String> seen = new HashSet<>();
    for (CommitteeSeat seat : request.committee()) {
      Member member = memberRepository.findById(seat.memberId()).orElse(null);
      if (member == null || !id.equals(member.getCooperativeId())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Everyone on the committee must be a member of this co-operative"));
      }
      if (!"Active".equals(member.getStatus())) {
        return ResponseEntity.badRequest().body(Map.of("error", member.getFullName() + " isn't an active member"));
      }
      if (!seen.add(member.getId())) {
        return ResponseEntity.badRequest().body(Map.of("error", member.getFullName() + " is on the committee twice"));
      }
    }

    coop.setLoanRules(
        request.minAmount(),
        request.savingsMultiplier(),
        request.withinPercent(),
        request.minMembershipMonths(),
        request.minSavings(),
        request.allowConcurrentLoans(),
        request.minGuarantors());
    coop.setDividendRules(request.dividendMethod(), request.dividendRate(), request.dividendFrequency());
    coop.setLoanApplicationFee(request.applicationFee());
    cooperativeRepository.save(coop);

    committeeRepository.deleteAllForCooperative(id);
    committeeRepository.flush();
    int position = 1;
    for (CommitteeSeat seat : request.committee()) {
      committeeRepository.save(new LoanCommitteeMember(id, position++, seat.memberId(), seat.title().trim()));
    }

    auditLogService.log(caller.getId(), caller.getRole(), "Settings", "Update", "Loan settings", "Success", httpRequest);
    return ResponseEntity.ok(dtoOf(coop, true));
  }

  private boolean canEdit(Member caller, String coopId) {
    if ("super_admin".equals(caller.getRole())) return true;
    boolean admin = "admin".equals(caller.getRole()) && coopId.equals(caller.getId()) && coopId.equals(caller.getCooperativeId());
    return admin
        || (coopId.equals(caller.getCooperativeId())
            && caller.getCoopRoleId() != null
            && coopPermissions.allows(caller, coopId, "Settings", null, true));
  }

  private LoanSettingsDto dtoOf(Cooperative coop, boolean canEdit) {
    List<LoanCommitteeMember> seats = committeeRepository.findAllByCooperativeIdOrderByPositionAsc(coop.getId());
    Map<String, String> names =
        memberRepository.findAllById(seats.stream().map(LoanCommitteeMember::getMemberId).toList()).stream()
            .collect(java.util.stream.Collectors.toMap(Member::getId, Member::getFullName));
    List<CommitteeSeatDto> committee =
        seats.stream()
            .map(s -> new CommitteeSeatDto(s.getPosition(), s.getMemberId(), names.getOrDefault(s.getMemberId(), s.getMemberId()), s.getTitle()))
            .toList();
    return new LoanSettingsDto(
        coop.getLoanMinAmount(),
        coop.getLoanApplicationFee(),
        coop.getLoanSavingsMultiplier(),
        coop.getLoanWithinPercent(),
        coop.getLoanMinMembershipMonths(),
        coop.getLoanMinSavings(),
        coop.isLoanAllowConcurrent(),
        coop.getLoanMinGuarantors(),
        coop.getDividendMethod(),
        coop.getDividendRate(),
        coop.getDividendFrequency(),
        committee,
        committee.isEmpty(),
        canEdit);
  }
}
