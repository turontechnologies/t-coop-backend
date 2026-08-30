package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A borrower's own loan-installment repayment (Paystack initialize/confirm, mirroring
 * {@code SavingsSelfServiceController}'s deposit pattern exactly), and an admin/coop-staff's
 * manual entry after receiving payment offline. Every repayment pays exactly one fixed
 * installment — this app doesn't support partial or arbitrary-amount repayments, so the amount is
 * always computed server-side from the loan's own terms, never client-supplied.
 */
@RestController
public class LoanRepaymentController {

  private static final Integer SETTINGS_SINGLETON_ID = 1;

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanPaymentIntentRepository intentRepository;
  private final LoanRepaymentRepository loanRepaymentRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final PaymentGatewayService paymentGatewayService;
  private final AuditLogService auditLogService;

  public LoanRepaymentController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanPaymentIntentRepository intentRepository,
      LoanRepaymentRepository loanRepaymentRepository,
      PlatformSettingsRepository platformSettingsRepository,
      PaymentGatewayService paymentGatewayService,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.intentRepository = intentRepository;
    this.loanRepaymentRepository = loanRepaymentRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.paymentGatewayService = paymentGatewayService;
    this.auditLogService = auditLogService;
  }

  /** What's due next — the borrower's own "Make a Repayment" button and the admin's "Record
   * Repayment" button both call this first to know the exact amount before submitting. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/next")
  public ResponseEntity<?> next(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireLoanParty(authentication, id, loanId);
    if (access.error() != null) return access.error();

    LoanRecord loan = access.loan();
    if (!"Active".equals(loan.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan isn't active"));
    }
    if (loan.getRepaymentsMade() >= loan.getNumberOfRepayments()) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan is already fully repaid"));
    }

    int installmentNumber = loan.getRepaymentsMade() + 1;
    BigDecimal amount = nextInstallmentAmount(loan, installmentNumber);
    return ResponseEntity.ok(new NextInstallmentDto(installmentNumber, amount));
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/initialize")
  public ResponseEntity<?> initialize(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireBorrower(authentication, id, loanId);
    if (access.error() != null) return access.error();

    LoanRecord loan = access.loan();
    var dueCheck = requireDue(loan);
    if (dueCheck.error() != null) return dueCheck.error();

    PlatformSettings settings = platformSettings();
    if (!settings.isPaystackEnabled() || !notBlank(settings.getPaystackPublicKey())) {
      return ResponseEntity.status(409).body(Map.of("error", "Paystack isn't set up for this platform yet."));
    }

    String reference = "LNRPY-" + id + "-" + System.currentTimeMillis();
    intentRepository.save(
        new LoanPaymentIntent(
            reference, loan.getId(), id, access.caller().getId(), dueCheck.installmentNumber(), dueCheck.amount()));

    return ResponseEntity.ok(
        new InitializeLoanRepaymentResponseDto(
            reference, dueCheck.installmentNumber(), dueCheck.amount(), settings.getPaystackPublicKey()));
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/confirm")
  public ResponseEntity<?> confirm(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody ConfirmLoanRepaymentRequest request,
      HttpServletRequest httpRequest) {
    var access = requireBorrower(authentication, id, loanId);
    if (access.error() != null) return access.error();

    LoanPaymentIntent intent = intentRepository.findById(request.reference()).orElse(null);
    if (intent == null
        || !intent.getLoanId().equals(access.loan().getId())
        || !intent.getMemberId().equals(access.caller().getId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that payment."));
    }
    if ("Confirmed".equals(intent.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That payment was already confirmed."));
    }

    PlatformSettings settings = platformSettings();
    PaymentGatewayService.VerificationResult verification =
        paymentGatewayService.verifyPaystack(intent.getReference(), settings.getPaystackSecretKey());

    if (!verification.success()) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402)
          .body(Map.of("error", verification.message() != null ? verification.message() : "Payment verification failed."));
    }
    if (verification.amountPaid().compareTo(intent.getAmount()) < 0) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402)
          .body(Map.of("error", "The amount paid doesn't match what was expected."));
    }

    intent.setStatus("Confirmed");
    intentRepository.save(intent);

    LoanRepayment repayment =
        recordRepayment(
            access.loan(), id, access.caller().getId(), intent.getInstallmentNumber(), intent.getAmount(),
            "Paystack", intent.getReference());

    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Loans", "Update", "Repayment", "Success", httpRequest);

    return ResponseEntity.ok(toResult(repayment, access.loan()));
  }

  /** Admin/coop-staff's manual entry after receiving payment offline — no Paystack, and (like the
   * self-service side) no client-supplied amount; it's always the loan's own next fixed
   * installment. */
  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/manual")
  public ResponseEntity<?> manual(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    LoanRecord loan = findLoan(loanId);
    if (loan == null || !id.equals(loan.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    }
    var dueCheck = requireDue(loan);
    if (dueCheck.error() != null) return dueCheck.error();

    String transactionId = "MANUAL-LNRPY-" + id + "-" + System.currentTimeMillis();
    LoanRepayment repayment =
        recordRepayment(
            loan, id, loan.getMemberId(), dueCheck.installmentNumber(), dueCheck.amount(),
            "Manual Upload", transactionId);

    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Loans", "Update", "Repayment", "Success", httpRequest);

    return ResponseEntity.ok(toResult(repayment, loan));
  }

  /** Staff sees any loan in their own co-op; the borrower (or named guarantor, for visibility)
   * sees only their own. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments")
  public ResponseEntity<?> list(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireLoanParty(authentication, id, loanId);
    if (access.error() != null) return access.error();

    List<LoanRepaymentDto> dtos =
        loanRepaymentRepository.findAllByLoanIdOrderByCreatedAtDesc(access.loan().getId()).stream()
            .map(LoanRepaymentDto::from)
            .toList();
    return ResponseEntity.ok(dtos);
  }

  private LoanRepayment recordRepayment(
      LoanRecord loan,
      String cooperativeId,
      String memberId,
      int installmentNumber,
      BigDecimal amount,
      String method,
      String transactionId) {
    LoanRepayment repayment =
        new LoanRepayment(loan.getId(), cooperativeId, memberId, installmentNumber, amount, method, transactionId);
    loanRepaymentRepository.save(repayment);

    loan.incrementRepaymentsMade();
    if (loan.getRepaymentsMade() >= loan.getNumberOfRepayments()) {
      loan.setStatus("Completed");
    }
    loanRecordRepository.save(loan);
    return repayment;
  }

  private LoanRepaymentResultDto toResult(LoanRepayment repayment, LoanRecord loan) {
    Member member = memberRepository.findById(loan.getMemberId()).orElse(null);
    LoanType type = loanTypeRepository.findById(loan.getLoanTypeId()).orElse(null);
    return new LoanRepaymentResultDto(
        LoanRepaymentDto.from(repayment),
        LoanRecordDto.from(
            loan,
            member != null ? member.getFullName() : "Unknown member",
            type != null ? type.getName() : "Unknown type"));
  }

  /** The next installment's exact amount — the loan's flat {@code monthlyRepayment} for every
   * installment except the last, which instead takes whatever's left of {@code totalRepayment}
   * after every prior repayment, so rounding drift from dividing totalRepayment by
   * numberOfRepayments never leaves the loan a few kobo short (or over) of fully repaid. */
  private BigDecimal nextInstallmentAmount(LoanRecord loan, int installmentNumber) {
    if (installmentNumber < loan.getNumberOfRepayments()) {
      return loan.getMonthlyRepayment().setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal alreadyPaid = loanRepaymentRepository.sumByLoan(loan.getId());
    return loan.getTotalRepayment().subtract(alreadyPaid).setScale(2, RoundingMode.HALF_UP);
  }

  private record DueCheck(int installmentNumber, BigDecimal amount, ResponseEntity<?> error) {}

  private DueCheck requireDue(LoanRecord loan) {
    if (!"Active".equals(loan.getStatus())) {
      return new DueCheck(0, null, ResponseEntity.status(409).body(Map.of("error", "This loan isn't active")));
    }
    if (loan.getRepaymentsMade() >= loan.getNumberOfRepayments()) {
      return new DueCheck(
          0, null, ResponseEntity.status(409).body(Map.of("error", "This loan is already fully repaid")));
    }
    int installmentNumber = loan.getRepaymentsMade() + 1;
    return new DueCheck(installmentNumber, nextInstallmentAmount(loan, installmentNumber), null);
  }

  private LoanRecord findLoan(String id) {
    try {
      return loanRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private PlatformSettings platformSettings() {
    return platformSettingsRepository
        .findById(SETTINGS_SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }

  private record LoanAccess(Member caller, LoanRecord loan, ResponseEntity<?> error) {}

  /** Strictly the loan's own borrower (or super admin) — used for the actual repayment actions,
   * where only the person who owes the money may pay it. */
  private LoanAccess requireBorrower(Authentication authentication, String cooperativeId, String loanId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new LoanAccess(null, null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    LoanRecord loan = findLoan(loanId);
    if (loan == null || !cooperativeId.equals(loan.getCooperativeId())) {
      return new LoanAccess(null, null, ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan")));
    }
    if (!"super_admin".equals(caller.getRole()) && !caller.getId().equals(loan.getMemberId())) {
      return new LoanAccess(
          null, null, ResponseEntity.status(403).body(Map.of("error", "Only this loan's borrower can repay it")));
    }
    return new LoanAccess(caller, loan, null);
  }

  /** Anyone with legitimate visibility into this loan: its borrower, its named guarantor, staff
   * of its own co-op, or a super admin — used for read-only lookups (next installment, repayment
   * history). */
  private LoanAccess requireLoanParty(Authentication authentication, String cooperativeId, String loanId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new LoanAccess(null, null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    LoanRecord loan = findLoan(loanId);
    if (loan == null || !cooperativeId.equals(loan.getCooperativeId())) {
      return new LoanAccess(null, null, ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan")));
    }
    boolean isStaff =
        "admin".equals(caller.getRole()) || "super_admin".equals(caller.getRole()) || caller.getCoopRoleId() != null;
    boolean isParty = caller.getId().equals(loan.getMemberId()) || caller.getId().equals(loan.getGuarantorId());
    if (!isStaff && !isParty) {
      return new LoanAccess(
          null, null, ResponseEntity.status(403).body(Map.of("error", "You can't view that loan")));
    }
    return new LoanAccess(caller, loan, null);
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

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
