package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.coopstaff.CoopOffices;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import com.turontechnologies.tcoop.savings.SavingsRecord;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
 * The real backend behind a member's own "Take a Loan" application, the named guarantor's
 * accept/reject, and the officers' approval chain — the self-service counterpart to
 * {@link LoanController}'s read-only oversight.
 *
 * <p>A loan's guarantor is always an active member of the same co-op ({@code
 * loan_records.guarantor_id} is a hard FK). Once they accept, the loan moves through the co-op's
 * officers in order — Vice President, Treasurer, Secretary (see {@link CoopOffices}) — and the
 * Secretary's approval pays it out to the member's bank account server-side, from the co-op's
 * own Paystack account if it connected one (Settings -> Integrations), else the platform's. The
 * President can reject at any stage. When an office is vacant (or its holder is the borrower or
 * guarantor), the co-op admin covers that step.
 */
@RestController
public class LoanSelfServiceController {

  private static final Integer SETTINGS_SINGLETON_ID = 1;
  private static final String AWAITING_GUARANTOR = "Awaiting Guarantor";

  /** The approval chain, in order. */
  private static final List<String> CHAIN =
      List.of(CoopOffices.VICE_PRESIDENT, CoopOffices.TREASURER, CoopOffices.SECRETARY);

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanApprovalRepository loanApprovalRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final CoopOffices coopOffices;
  private final CoopGatewayResolver gatewayResolver;
  private final PaymentGatewayService paymentGatewayService;
  private final NotificationService notificationService;
  private final AuditLogService auditLogService;

  public LoanSelfServiceController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanApprovalRepository loanApprovalRepository,
      SavingsRecordRepository savingsRecordRepository,
      PlatformSettingsRepository platformSettingsRepository,
      CoopOffices coopOffices,
      CoopGatewayResolver gatewayResolver,
      PaymentGatewayService paymentGatewayService,
      NotificationService notificationService,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanApprovalRepository = loanApprovalRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.coopOffices = coopOffices;
    this.gatewayResolver = gatewayResolver;
    this.paymentGatewayService = paymentGatewayService;
    this.notificationService = notificationService;
    this.auditLogService = auditLogService;
  }

  /** What the member will actually receive once the Secretary approves — principal minus the
   * combined co-op + platform loans charge. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/disbursement")
  public ResponseEntity<?> disbursementPreview(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireMemberOfCoop(authentication, id);
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

    String mode = request.loanMode() == null || request.loanMode().isBlank()
        ? LoanEligibility.TIMES_TWO
        : request.loanMode();
    if (!LoanEligibility.isValidMode(mode)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Choose \"Times two\" or \"Within\""));
    }
    boolean within = LoanEligibility.WITHIN.equals(mode);

    // A Within loan is secured by the member's own savings, so it has no guarantor.
    Member guarantor = null;
    if (!within) {
      if (!notBlank(request.guarantorMemberId())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Select a guarantor"));
      }
      guarantor = memberRepository.findById(request.guarantorMemberId()).orElse(null);
      if (guarantor == null || !id.equals(guarantor.getCooperativeId())) {
        return ResponseEntity.status(400)
            .body(Map.of("error", "Your guarantor must be a member of this co-operative"));
      }
      if (!"Active".equals(guarantor.getStatus())) {
        return ResponseEntity.status(400)
            .body(Map.of("error", guarantor.getFullName() + " isn't an active member, so they can't guarantee a loan"));
      }
      if (guarantor.getId().equals(member.getId())) {
        return ResponseEntity.status(409).body(Map.of("error", "You can't guarantee your own loan"));
      }
    }

    BigDecimal totalSavings = savingsRecordRepository.sumByMember(member.getId());
    BigDecimal eligibility = LoanEligibility.forMode(totalSavings, type, mode);
    if (request.amount().compareTo(eligibility) > 0) {
      return ResponseEntity.status(409)
          .body(
              Map.of(
                  "error",
                  within
                      ? "A Within loan can't be more than your savings (or this loan type's maximum)"
                      : "A Times two loan can't be more than half your savings (or this loan type's maximum)"));
    }

    // Interest follows the loan type — a percentage of the amount, a fixed amount, or none for a
    // "NoInterest" type. The record keeps the effective percentage so "x% flat" displays stay
    // right for a fixed-amount type too.
    BigDecimal totalInterest = LoanEligibility.totalInterest(type, request.amount());
    BigDecimal interestRate =
        totalInterest.multiply(BigDecimal.valueOf(100)).divide(request.amount(), 2, RoundingMode.HALF_UP);
    BigDecimal totalRepayment = request.amount().add(totalInterest);
    int numberOfRepayments = type.getDurationMonths();
    BigDecimal monthlyRepayment =
        totalRepayment.divide(BigDecimal.valueOf(numberOfRepayments), 2, RoundingMode.HALF_UP);

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
            guarantor != null ? guarantor.getId() : null,
            guarantor != null ? guarantor.getFullName() : null,
            within ? statusFor(CHAIN.get(0)) : AWAITING_GUARANTOR,
            mode);
    loanRecordRepository.save(record);

    if (within) {
      notifyApprover(record, CHAIN.get(0), member.getFullName(), type.getName());
    } else {
      notificationService.notify(
          guarantor.getId(),
          "LOAN_GUARANTOR_REQUEST",
          "You've been asked to guarantee a loan",
          member.getFullName() + " has asked you to guarantee a " + type.getName() + " loan.",
          "/loans/" + record.getId());
    }

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
    if (!AWAITING_GUARANTOR.equals(record.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan already moved past guarantor review"));
    }

    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    String applicantName = applicant != null ? applicant.getFullName() : "A member";
    String typeName = typeNameOf(record);

    if ("Accepted".equals(request.decision())) {
      String firstStep = CHAIN.get(0);
      record.setStatus(statusFor(firstStep));
      record.setGuarantorAcceptedAt(LocalDateTime.now());
      if (notBlank(request.documentUrl())) {
        record.setGuarantorDocumentUrl(request.documentUrl());
      }
      loanRecordRepository.save(record);
      notifyApprover(record, firstStep, applicantName, typeName);
    } else {
      record.setStatus("Rejected");
      record.setRejectionReason("Guarantor declined to stand for this loan.");
      loanRecordRepository.save(record);
      if (applicant != null) {
        notificationService.notify(
            applicant.getId(),
            "LOAN_GUARANTOR_REJECTED",
            "Your loan application was declined",
            "Your guarantor declined to stand for your " + typeName + " loan application.",
            "/loans/" + record.getId());
      }
    }

    auditLogService.log(
        caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);

    return ResponseEntity.ok(
        LoanRecordDto.from(record, applicant != null ? applicant.getFullName() : "Unknown member", typeName));
  }

  /** Where the loan is in the approval chain, and whether the caller can act on it. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/approvals")
  public ResponseEntity<?> approvals(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    }
    boolean isParty =
        caller.getId().equals(record.getMemberId()) || caller.getId().equals(record.getGuarantorId());
    boolean isStaff =
        "super_admin".equals(caller.getRole()) || "admin".equals(caller.getRole()) || caller.getCoopRoleId() != null;
    if (!isParty && !isStaff) {
      return ResponseEntity.status(403).body(Map.of("error", "You can't view this loan's approvals"));
    }

    List<LoanApprovalStatusDto.Step> chain =
        CHAIN.stream()
            .map(
                office ->
                    new LoanApprovalStatusDto.Step(
                        office,
                        CoopOffices.nameOf(office),
                        coopOffices.holderOf(id, office).map(Member::getFullName).orElse(null)))
            .toList();
    List<LoanApprovalStatusDto.Decision> trail =
        loanApprovalRepository.findAllByLoanIdOrderByActedAtAsc(record.getId()).stream()
            .map(approval -> LoanApprovalStatusDto.Decision.from(approval, CoopOffices.nameOf(approval.getStep())))
            .toList();

    Authority authority = authorityFor(caller, record);
    BigDecimal net = null;
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop != null) {
      net = record.getAmount().subtract(loansChargeFor(coop, platformSettings(), record.getAmount()));
    }

    return ResponseEntity.ok(
        new LoanApprovalStatusDto(
            chain,
            trail,
            officeForStatus(record.getStatus()),
            authority.canApprove(),
            authority.canReject(),
            authority.actingAsLabel(),
            net));
  }

  /**
   * An officer's decision on the loan's current step. Approving at Vice President or Treasurer
   * moves it to the next office; approving at Secretary pays it out to the member's bank account
   * and makes it Active — if the payout fails, nothing is recorded and the Secretary can retry
   * (the transfer reference is fixed per loan, so a retry can never pay twice).
   */
  @PatchMapping("/api/v1/cooperatives/{id}/loans/{loanId}/decision")
  public ResponseEntity<?> decide(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody LoanDecisionRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    }

    boolean approving = "Approved".equals(request.decision());
    Authority authority = authorityFor(caller, record);
    if (approving ? !authority.canApprove() : !authority.canReject()) {
      return ResponseEntity.status(403)
          .body(Map.of("error", approving ? "This loan isn't waiting for your approval" : "You can't reject this loan"));
    }

    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    String applicantName = applicant != null ? applicant.getFullName() : "Unknown member";
    String typeName = typeNameOf(record);

    if (!approving) {
      String reason = notBlank(request.rejectionReason())
          ? request.rejectionReason().trim()
          : "Declined by the " + CoopOffices.nameOf(authority.office()) + ".";
      record.setStatus("Rejected");
      record.setRejectionReason(reason);
      loanRecordRepository.save(record);
      saveDecision(record, authority, caller, "Rejected", reason);
      if (applicant != null) {
        notificationService.notify(
            applicant.getId(),
            "LOAN_DECISION",
            "Your loan was declined",
            "Your " + typeName + " loan application was declined: " + reason,
            "/loans/" + record.getId());
      }
      auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Warning", httpRequest);
      return ResponseEntity.ok(LoanRecordDto.from(record, applicantName, typeName));
    }

    String step = authority.office();
    int stepIndex = CHAIN.indexOf(step);
    boolean finalStep = stepIndex == CHAIN.size() - 1;

    if (!finalStep) {
      String nextStep = CHAIN.get(stepIndex + 1);
      record.setStatus(statusFor(nextStep));
      loanRecordRepository.save(record);
      saveDecision(record, authority, caller, "Approved", null);
      notifyApprover(record, nextStep, applicantName, typeName);
      auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);
      return ResponseEntity.ok(LoanRecordDto.from(record, applicantName, typeName));
    }

    // Final step — pay the loan out before recording the approval.
    if (applicant == null || !notBlank(applicant.getBankCode()) || !notBlank(applicant.getAccountNumber())) {
      return ResponseEntity.status(409)
          .body(Map.of("error", applicantName + " hasn't added a verified bank account yet, so the loan can't be paid out."));
    }
    String payoutKey = gatewayResolver.payoutSecretKeyFor(id).orElse(null);
    if (payoutKey == null) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "No Paystack account is set up to pay out loans. Connect one in Settings → Integrations."));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    // A Within loan comes out of the member's savings — they may have withdrawn since applying.
    if (record.isWithin()
        && savingsRecordRepository.sumByMember(record.getMemberId()).compareTo(record.getAmount()) < 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", applicantName + "'s savings no longer cover this Within loan, so it can't be paid out."));
    }
    BigDecimal net = record.getAmount().subtract(loansChargeFor(coop, platformSettings(), record.getAmount()));
    String reference = "loan-" + record.getId().toString().toLowerCase();

    PaymentGatewayService.TransferResult transfer =
        paymentGatewayService.initiatePaystackTransfer(
            payoutKey,
            notBlank(applicant.getAccountName()) ? applicant.getAccountName() : applicantName,
            applicant.getAccountNumber(),
            applicant.getBankCode(),
            net,
            coop.getName() + " loan disbursement — " + typeName,
            reference);
    if (!transfer.success()) {
      return ResponseEntity.status(502)
          .body(Map.of("error", transfer.message() != null ? transfer.message() : "The payout didn't go through."));
    }

    record.markDisbursed(reference, transfer.status(), net);
    record.setStatus("Active");
    loanRecordRepository.save(record);
    saveDecision(record, authority, caller, "Approved", null);

    if (record.isWithin()) {
      // The loan is borrowed from the member's own savings — take the principal out of them.
      BigDecimal balanceAfter =
          savingsRecordRepository.sumByMember(record.getMemberId()).subtract(record.getAmount());
      savingsRecordRepository.save(
          new SavingsRecord(
              id,
              record.getMemberId(),
              null,
              record.getAmount().negate(),
              balanceAfter,
              "Loan Disbursement",
              reference,
              "Success",
              null));
    }

    notificationService.notify(
        applicant.getId(),
        "LOAN_DISBURSED",
        "Your loan has been paid out",
        "Your " + typeName + " loan was approved and " + net + " has been sent to your bank account.",
        "/loans/" + record.getId());

    auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);
    return ResponseEntity.ok(LoanRecordDto.from(record, applicantName, typeName));
  }

  /** What the caller may do on this loan right now. {@code office} is the step the decision is
   * recorded against; {@code covering} means the admin is standing in for a vacant office. */
  private record Authority(boolean canApprove, boolean canReject, String office, boolean covering) {
    static final Authority NONE = new Authority(false, false, null, false);

    String actingAsLabel() {
      if (office == null) return null;
      return covering ? "Admin (covering for " + CoopOffices.nameOf(office) + ")" : CoopOffices.nameOf(office);
    }
  }

  private Authority authorityFor(Member caller, LoanRecord record) {
    String cooperativeId = record.getCooperativeId();
    if (!cooperativeId.equals(caller.getCooperativeId())) return Authority.NONE;
    // Nobody acts on a loan they're the borrower or guarantor of.
    if (caller.getId().equals(record.getMemberId()) || caller.getId().equals(record.getGuarantorId())) {
      return Authority.NONE;
    }

    String step = officeForStatus(record.getStatus());
    if (step != null) {
      Member holder = eligibleHolder(cooperativeId, step, record);
      if (holder != null && holder.getId().equals(caller.getId())) {
        return new Authority(true, true, step, false);
      }
      if (holder == null && "admin".equals(caller.getRole())) {
        return new Authority(true, true, step, true);
      }
    }

    boolean stillOpen = step != null || AWAITING_GUARANTOR.equals(record.getStatus());
    if (stillOpen && CoopOffices.PRESIDENT.equals(coopOffices.officeOf(caller))) {
      return new Authority(false, true, CoopOffices.PRESIDENT, false);
    }
    return Authority.NONE;
  }

  /** The member who should act on this step — the office holder, unless the office is vacant or
   * held by the borrower/guarantor themselves, in which case null (the admin covers it). */
  private Member eligibleHolder(String cooperativeId, String office, LoanRecord record) {
    return coopOffices
        .holderOf(cooperativeId, office)
        .filter(holder -> !holder.getId().equals(record.getMemberId()))
        .filter(holder -> !holder.getId().equals(record.getGuarantorId()))
        .orElse(null);
  }

  private void notifyApprover(LoanRecord record, String office, String applicantName, String typeName) {
    String officeName = CoopOffices.nameOf(office);
    String link = "/loans/request/" + record.getId();
    Member holder = eligibleHolder(record.getCooperativeId(), office, record);
    if (holder != null) {
      notificationService.notify(
          holder.getId(),
          "LOAN_APPROVAL_REQUEST",
          "A loan needs your approval",
          applicantName + "'s " + typeName + " loan is waiting for your approval as " + officeName + ".",
          link);
    } else {
      notificationService.notifyCoopAdmin(
          record.getCooperativeId(),
          "LOAN_APPROVAL_REQUEST",
          "A loan needs " + officeName + " approval",
          applicantName + "'s " + typeName + " loan is waiting for " + officeName
              + " approval. No one else can act on that step, so you can approve it.",
          link);
    }
  }

  private void saveDecision(LoanRecord record, Authority authority, Member caller, String decision, String note) {
    loanApprovalRepository.save(
        new LoanApproval(
            record.getId(), authority.office(), decision, caller.getId(), caller.getFullName(), authority.covering(), note));
  }

  private static String statusFor(String office) {
    return "Awaiting " + CoopOffices.nameOf(office);
  }

  /** The chain office a status is waiting on, or null when it's not in the chain. */
  private static String officeForStatus(String status) {
    for (String office : CHAIN) {
      if (statusFor(office).equals(status)) return office;
    }
    return null;
  }

  private String typeNameOf(LoanRecord record) {
    return loanTypeRepository.findById(record.getLoanTypeId()).map(LoanType::getName).orElse("Unknown type");
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
}
