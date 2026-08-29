package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
 * The real backend behind a member's own "Take a Loan" application, the named guarantor's
 * accept/reject, and an admin's approve/reject decision — the self-service counterpart to
 * {@link LoanController}'s read-only oversight. A loan's guarantor here is always a real existing
 * co-op member ({@code loan_records.guarantor_id} is a hard FK), unlike the separate email-invite
 * guarantor workflow used for member creation — a simpler, in-app accept/reject by an
 * authenticated existing member, via {@link NotificationService} same as everywhere else.
 */
@RestController
public class LoanSelfServiceController {

  private static final Integer SETTINGS_SINGLETON_ID = 1;

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final NotificationService notificationService;
  private final AuditLogService auditLogService;

  public LoanSelfServiceController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      SavingsRecordRepository savingsRecordRepository,
      PlatformSettingsRepository platformSettingsRepository,
      NotificationService notificationService,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.notificationService = notificationService;
    this.auditLogService = auditLogService;
  }

  /** What the admin needs before initiating the real Paystack Transfer payout — call this,
   * transfer {@code netDisbursed}, then call {@link #decide} with the resulting reference. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/disbursement")
  public ResponseEntity<?> disbursementPreview(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    BigDecimal chargeAmount = loansChargeFor(coop, platformSettings(), record.getAmount());
    return ResponseEntity.ok(
        new LoanDisbursementDto(record.getAmount(), chargeAmount, record.getAmount().subtract(chargeAmount)));
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans")
  public ResponseEntity<?> apply(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody LoanApplicationRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member member = access.caller();

    LoanType type = findType(request.loanTypeId(), id);
    if (type == null || !"Active".equals(type.getStatus())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan type"));
    }

    Member guarantor = memberRepository.findById(request.guarantorMemberId()).orElse(null);
    if (guarantor == null || !id.equals(guarantor.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that guarantor"));
    }
    if (guarantor.getId().equals(member.getId())) {
      return ResponseEntity.status(409).body(Map.of("error", "You can't guarantee your own loan"));
    }

    BigDecimal totalSavings = savingsRecordRepository.sumByMember(member.getId());
    BigDecimal eligibility = LoanEligibility.forType(totalSavings, type);
    if (request.amount().compareTo(eligibility) > 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That amount exceeds your loan eligibility for this type"));
    }

    // Interest is always applied as a flat percentage of principal regardless of interestType —
    // same simplification the frontend's own loan calculator has always used (see
    // activeLoanTypeDefs' javadoc in admin-settings-data.ts): a "Fixed" type still only carries a
    // percentage-shaped number in interestAmount, so there's nothing else to branch on here.
    BigDecimal interestRate = type.getInterestAmount();
    BigDecimal totalInterest =
        request.amount().multiply(interestRate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    BigDecimal totalRepayment = request.amount().add(totalInterest);
    int numberOfRepayments = type.getDurationMonths();
    BigDecimal monthlyRepayment =
        totalRepayment.divide(BigDecimal.valueOf(numberOfRepayments), 2, java.math.RoundingMode.HALF_UP);

    LoanRecord record =
        new LoanRecord(
            id,
            member.getId(),
            type.getId(),
            request.amount(),
            interestRate,
            type.getDurationMonths(),
            numberOfRepayments,
            monthlyRepayment,
            totalRepayment,
            guarantor.getId(),
            guarantor.getFullName(),
            "Awaiting Guarantor");
    loanRecordRepository.save(record);

    notificationService.notify(
        guarantor.getId(),
        "LOAN_GUARANTOR_REQUEST",
        "You've been asked to guarantee a loan",
        member.getFullName() + " has asked you to guarantee a " + type.getName() + " loan.",
        "/loans");

    auditLogService.log(
        member.getId(), member.getRole(), "Loans", "Create", type.getName(), "Success", httpRequest);

    return ResponseEntity.ok(LoanRecordDto.from(record, member.getFullName(), type.getName()));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/loans/{loanId}/guarantor-response")
  public ResponseEntity<?> guarantorResponse(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody GuarantorResponseRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    }
    if (!caller.getId().equals(record.getGuarantorId())) {
      return ResponseEntity.status(403).body(Map.of("error", "You aren't this loan's named guarantor"));
    }
    if (!"Awaiting Guarantor".equals(record.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan already moved past guarantor review"));
    }

    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    LoanType type = loanTypeRepository.findById(record.getLoanTypeId()).orElse(null);
    String typeName = type != null ? type.getName() : "Unknown type";

    if ("Accepted".equals(request.decision())) {
      record.setStatus("Awaiting Admin");
      record.setGuarantorAcceptedAt(LocalDateTime.now());
      if (notBlank(request.documentUrl())) {
        record.setGuarantorDocumentUrl(request.documentUrl());
      }
      notificationService.notifyCoopAdmin(
          id,
          "LOAN_GUARANTOR_ACCEPTED",
          "A loan is ready for your decision",
          (applicant != null ? applicant.getFullName() : "A member") + "'s " + typeName + " loan was accepted by its guarantor and now awaits your approval.",
          "/loans");
    } else {
      String reason = "Guarantor declined to stand for this loan.";
      record.setStatus("Rejected");
      record.setRejectionReason(reason);
      if (applicant != null) {
        notificationService.notify(
            applicant.getId(),
            "LOAN_GUARANTOR_REJECTED",
            "Your loan application was declined",
            "Your guarantor declined to stand for your " + typeName + " loan application.",
            "/loans");
      }
    }
    loanRecordRepository.save(record);

    auditLogService.log(
        caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);

    return ResponseEntity.ok(
        LoanRecordDto.from(record, applicant != null ? applicant.getFullName() : "Unknown member", typeName));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/loans/{loanId}/decision")
  public ResponseEntity<?> decide(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody LoanDecisionRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    }
    if (!"Awaiting Admin".equals(record.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan isn't awaiting your decision"));
    }

    if ("Approved".equals(request.decision())) {
      if (!notBlank(request.transferReference())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Missing transfer reference"));
      }
      record.setStatus("Active");
    } else {
      record.setStatus("Rejected");
      record.setRejectionReason(notBlank(request.rejectionReason()) ? request.rejectionReason() : "Declined by admin.");
    }
    loanRecordRepository.save(record);

    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    LoanType type = loanTypeRepository.findById(record.getLoanTypeId()).orElse(null);
    String typeName = type != null ? type.getName() : "Unknown type";

    if (applicant != null) {
      notificationService.notify(
          applicant.getId(),
          "LOAN_DECISION",
          "Approved".equals(request.decision()) ? "Your loan was approved" : "Your loan was declined",
          "Approved".equals(request.decision())
              ? "Your " + typeName + " loan of " + record.getAmount() + " has been disbursed."
              : "Your " + typeName + " loan application was declined.",
          "/loans");
    }

    auditLogService.log(
        access.caller().getId(),
        access.caller().getRole(),
        "Loans",
        "Update",
        typeName,
        "Approved".equals(request.decision()) ? "Success" : "Warning",
        httpRequest);

    return ResponseEntity.ok(
        LoanRecordDto.from(record, applicant != null ? applicant.getFullName() : "Unknown member", typeName));
  }

  private LoanType findType(String typeId, String cooperativeId) {
    try {
      LoanType type = loanTypeRepository.findById(UUID.fromString(typeId)).orElse(null);
      return type != null && cooperativeId.equals(type.getCooperativeId()) ? type : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private LoanRecord findRecord(String id) {
    try {
      return loanRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private BigDecimal feeFor(String type, BigDecimal feeAmount, BigDecimal amount) {
    if ("Fixed".equals(type)) return feeAmount;
    return amount.multiply(feeAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /** The co-op's own loans-charge rate combined with the platform's, deducted from disbursement
   * (see savings.SavingsSelfServiceController.savingsChargeFor for the same mechanics applied to
   * deposits). The loan's own principal/repayment terms are untouched by this — the member still
   * owes and repays the full approved amount regardless of what actually gets transferred. */
  private BigDecimal loansChargeFor(Cooperative coop, PlatformSettings settings, BigDecimal loanAmount) {
    BigDecimal coopCharge = feeFor(coop.getLoansChargeType(), coop.getLoansChargeAmount(), loanAmount);
    BigDecimal platformCharge = feeFor(settings.getLoansChargeType(), settings.getLoansChargeAmount(), loanAmount);
    return coopCharge.add(platformCharge);
  }

  private PlatformSettings platformSettings() {
    return platformSettingsRepository
        .findById(SETTINGS_SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

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
        null, ResponseEntity.status(403).body(Map.of("error", "You can only manage your own co-operative's loans")));
  }

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
        null, ResponseEntity.status(403).body(Map.of("error", "You can only manage your own co-operative's loans")));
  }
}
