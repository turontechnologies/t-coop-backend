package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.Amounts;
import com.turontechnologies.tcoop.notification.NotificationService;
import com.turontechnologies.tcoop.savings.SavingsRecord;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loan repayments, of any amount up to what's still owed: the borrower online (the co-op's
 * gateway), the borrower by bank transfer for the co-op to confirm, an admin recording a payment
 * received offline, and bulk uploads. A payment that covers half a month counts as half a
 * month; whole installments are worked out from the running total (see
 * {@link LoanRecord#applyRepayment}), and the loan completes when the total is reached.
 */
@RestController
public class LoanRepaymentController {

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanPaymentIntentRepository intentRepository;
  private final LoanRepaymentRepository loanRepaymentRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final CoopGatewayResolver gatewayResolver;
  private final AuditLogService auditLogService;
  private final LoanRepaymentClaimRepository claimRepository;
  private final NotificationService notificationService;
  private final CoopPermissions coopPermissions;

  public LoanRepaymentController(
      LoanRepaymentClaimRepository claimRepository,
      NotificationService notificationService,
      CoopPermissions coopPermissions,
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanPaymentIntentRepository intentRepository,
      LoanRepaymentRepository loanRepaymentRepository,
      SavingsRecordRepository savingsRecordRepository,
      CoopGatewayResolver gatewayResolver,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.intentRepository = intentRepository;
    this.loanRepaymentRepository = loanRepaymentRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.gatewayResolver = gatewayResolver;
    this.auditLogService = auditLogService;
    this.claimRepository = claimRepository;
    this.notificationService = notificationService;
    this.coopPermissions = coopPermissions;
  }

  /* ─────────────────────────────── What's owed ─────────────────────────────── */

  /**
   * Where the loan stands and what's due: the amount that finishes the current installment,
   * the amount for {@code count} installments, and the running totals the repay screens use to
   * show how much of the schedule any typed amount covers.
   */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/next")
  public ResponseEntity<?> next(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @RequestParam(required = false) Integer count) {
    var access = requireLoanParty(authentication, id, loanId);
    if (access.error() != null) return access.error();
    LoanRecord loan = access.loan();
    ResponseEntity<?> notDue = requireOwing(loan);
    if (notDue != null) return notDue;

    BigDecimal paid = LoanBalances.repaid(loan);
    int covered = LoanBalances.installmentsCovered(loan, paid);
    int remaining = loan.getNumberOfRepayments() - covered;
    int n = count == null ? 1 : Math.max(1, Math.min(count, remaining));
    BigDecimal outstanding = loan.getTotalRepayment().subtract(paid).max(BigDecimal.ZERO);
    return ResponseEntity.ok(
        new NextInstallmentDto(
            covered + 1,
            amountToClear(loan, paid, covered + n),
            n,
            remaining,
            amountToClear(loan, paid, covered + 1),
            claimRepository.existsByLoanIdAndStatus(loan.getId(), LoanRepaymentClaim.PENDING),
            loan.getMonthlyRepayment(),
            paid,
            outstanding,
            loan.getTotalRepayment(),
            loan.getNumberOfRepayments()));
  }

  /* ─────────────────────────────── Online (the borrower) ─────────────────────────────── */

  /** Optional amount: left out, it's what finishes the current installment. */
  public record PayRequest(BigDecimal amount) {}

  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/initialize")
  public ResponseEntity<?> initialize(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @RequestBody(required = false) PayRequest request) {
    var access = requireBorrower(authentication, id, loanId);
    if (access.error() != null) return access.error();
    LoanRecord loan = access.loan();
    var amountCheck = checkAmount(loan, request == null ? null : request.amount(), LoanBalances.repaid(loan));
    if (amountCheck.error() != null) return amountCheck.error();

    CoopGatewayResolver.Checkout checkout = gatewayResolver.checkoutFor(id).orElse(null);
    if (checkout == null) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "Online payments aren't set up for this co-operative yet. Pay by bank transfer instead."));
    }

    String reference = "LNRPY-" + id + "-" + System.currentTimeMillis();
    int installment = loan.getRepaymentsMade() + 1;
    intentRepository.save(
        new LoanPaymentIntent(
            reference, loan.getId(), id, access.caller().getId(), installment, amountCheck.amount(),
            checkout.gateway(), checkout.keyOwner()));
    return ResponseEntity.ok(
        new InitializeLoanRepaymentResponseDto(
            reference, installment, amountCheck.amount(), checkout.gateway(), checkout.publicKey()));
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/confirm")
  @Transactional
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

    PaymentGatewayService.VerificationResult verification =
        gatewayResolver.verify(id, intent.getGateway(), intent.getKeyOwner(), intent.getReference());
    if (!verification.success()) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402)
          .body(Map.of("error", verification.message() != null ? verification.message() : "Payment verification failed."));
    }
    if (verification.amountPaid().compareTo(intent.getAmount()) < 0) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402).body(Map.of("error", "The amount paid doesn't match what was expected."));
    }
    intent.setStatus("Confirmed");
    intentRepository.save(intent);

    // Money really arrived: never refuse it, but never count more than was owed.
    LoanRecord loan = access.loan();
    BigDecimal owed = owed(loan);
    BigDecimal amount = intent.getAmount().min(owed);
    if (amount.signum() <= 0) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan was already fully repaid. Contact your co-operative about a refund."));
    }
    LoanRepayment repayment =
        recordPayment(loan, id, access.caller().getId(), amount, intent.getGateway(), intent.getReference());
    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Loans", "Update", "Repayment", "Success", httpRequest);
    return ResponseEntity.ok(toResult(repayment, loan));
  }

  /* ─────────────────────────────── Recorded by the co-op ─────────────────────────────── */

  /** The receipt (required), an optional note, and the amount (left out: the current installment). */
  public record ManualRepaymentRequest(
      String receiptUrl, @Size(max = 500, message = "Keep the note under 500 characters") String note, BigDecimal amount) {}

  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/manual")
  @Transactional
  public ResponseEntity<?> manual(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody(required = false) ManualRepaymentRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    String receiptUrl = request == null || request.receiptUrl() == null ? "" : request.receiptUrl().trim();
    if (receiptUrl.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Upload the receipt for this payment before recording it."));
    }
    if (!receiptUrl.startsWith("https://") && !receiptUrl.startsWith("http://")) {
      return ResponseEntity.badRequest().body(Map.of("error", "That receipt upload didn't work. Please upload it again."));
    }
    LoanRecord loan = findLoan(loanId);
    if (loan == null || !id.equals(loan.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    }
    if (access.caller().getId().equals(loan.getMemberId())) {
      return ResponseEntity.status(403).body(Map.of("error", "Someone else has to record a repayment on your own loan"));
    }
    var amountCheck = checkAmount(loan, request.amount(), LoanBalances.repaid(loan));
    if (amountCheck.error() != null) return amountCheck.error();

    LoanRepayment repayment =
        recordPayment(loan, id, loan.getMemberId(), amountCheck.amount(), "Manual Upload",
            "MANUAL-LNRPY-" + id + "-" + System.currentTimeMillis());
    repayment.setManualProof(receiptUrl, access.caller().getFullName(), request.note());
    loanRepaymentRepository.save(repayment);
    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Loans", "Update", "Repayment", "Success", httpRequest);
    return ResponseEntity.ok(toResult(repayment, loan));
  }

  /** Staff see any loan in their co-op; the borrower and guarantors see their own. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments")
  public ResponseEntity<?> list(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireLoanParty(authentication, id, loanId);
    if (access.error() != null) return access.error();
    return ResponseEntity.ok(
        loanRepaymentRepository.findAllByLoanIdOrderByCreatedAtDesc(access.loan().getId()).stream()
            .map(LoanRepaymentDto::from)
            .toList());
  }

  /* ──────────────────── Repaid by bank transfer: member submits, co-op confirms ──────────────────── */

  public record TransferClaimRequest(
      BigDecimal amount,
      /** Older clients send installments instead of an amount. */
      Integer installments,
      String receiptUrl,
      @Size(max = 100) String transferReference,
      String paidOn,
      @Size(max = 500, message = "Keep the note under 500 characters") String note) {}

  /**
   * The borrower paid by bank transfer outside the platform and uploads the receipt. Nothing
   * counts yet: the co-op checks its bank and confirms it first (see {@link #decideClaim}).
   */
  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/transfer-claims")
  public ResponseEntity<?> submitClaim(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody TransferClaimRequest request,
      HttpServletRequest httpRequest) {
    var access = requireBorrower(authentication, id, loanId);
    if (access.error() != null) return access.error();
    LoanRecord loan = access.loan();
    if (claimRepository.existsByLoanIdAndStatus(loan.getId(), LoanRepaymentClaim.PENDING)) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "You already have a bank transfer waiting to be confirmed for this loan."));
    }
    String receiptUrl = request.receiptUrl() == null ? "" : request.receiptUrl().trim();
    if (!receiptUrl.startsWith("https://") && !receiptUrl.startsWith("http://")) {
      return ResponseEntity.badRequest().body(Map.of("error", "Upload the transfer receipt first."));
    }
    BigDecimal paid = LoanBalances.repaid(loan);
    BigDecimal wanted = request.amount();
    if (wanted == null && request.installments() != null) {
      wanted = amountToClear(loan, paid, LoanBalances.installmentsCovered(loan, paid) + request.installments());
    }
    var amountCheck = checkAmount(loan, wanted, paid);
    if (amountCheck.error() != null) return amountCheck.error();
    LocalDate paidOn;
    try {
      paidOn = request.paidOn() == null || request.paidOn().isBlank() ? LocalDate.now() : LocalDate.parse(request.paidOn().trim());
    } catch (DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Choose the date you made the transfer"));
    }
    if (paidOn.isAfter(LocalDate.now())) {
      return ResponseEntity.badRequest().body(Map.of("error", "The transfer date can't be in the future"));
    }

    BigDecimal amount = amountCheck.amount();
    int clears = LoanBalances.installmentsCovered(loan, paid.add(amount)) - LoanBalances.installmentsCovered(loan, paid);
    LoanRepaymentClaim claim =
        new LoanRepaymentClaim(
            loan.getId(), id, loan.getMemberId(), clears, amount, receiptUrl, request.transferReference(), paidOn, request.note());
    claimRepository.save(claim);

    String typeName = typeNameOf(loan);
    notificationService.notifyCoopAdmin(
        id,
        "LOAN_TRANSFER_REPAYMENT_SUBMITTED",
        "Bank transfer repayment to confirm",
        access.caller().getFullName() + " says they paid " + Amounts.naira(amount) + " by bank transfer for their "
            + typeName + " loan. Check your bank and confirm it.",
        "/loans/" + loan.getId());
    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Loans", "Create", "Transfer repayment", "Success", httpRequest);
    return ResponseEntity.ok(LoanRepaymentClaimDto.from(claim, access.caller().getFullName(), typeName));
  }

  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/transfer-claims")
  public ResponseEntity<?> loanClaims(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireLoanParty(authentication, id, loanId);
    if (access.error() != null) return access.error();
    Member borrower = memberRepository.findById(access.loan().getMemberId()).orElse(null);
    String name = borrower != null ? borrower.getFullName() : "Unknown member";
    String typeName = typeNameOf(access.loan());
    return ResponseEntity.ok(
        claimRepository.findAllByLoanIdOrderBySubmittedAtDesc(access.loan().getId()).stream()
            .map(c -> LoanRepaymentClaimDto.from(c, name, typeName))
            .toList());
  }

  /** The co-op's bank-transfer repayments, newest first ({@code status=Pending} for the to-do list). */
  @GetMapping("/api/v1/cooperatives/{id}/loans/repayment-claims")
  public ResponseEntity<?> coopClaims(
      Authentication authentication, @PathVariable String id, @RequestParam(required = false) String status) {
    var staff = requireLoanWriter(authentication, id, false);
    if (staff.error() != null) return staff.error();
    Map<String, String> names = new HashMap<>();
    for (Member m : memberRepository.findAllByCooperativeId(id)) names.put(m.getId(), m.getFullName());
    Map<UUID, String> typeNames = new HashMap<>();
    for (LoanType t : loanTypeRepository.findAllByCooperativeId(id)) typeNames.put(t.getId(), t.getName());
    Map<UUID, UUID> loanTypes = new HashMap<>();
    return ResponseEntity.ok(
        claimRepository.findAllByCooperativeIdOrderBySubmittedAtDesc(id).stream()
            .filter(c -> status == null || status.isBlank() || status.equals(c.getStatus()))
            .map(
                c -> {
                  UUID typeId =
                      loanTypes.computeIfAbsent(
                          c.getLoanId(), key -> loanRecordRepository.findById(key).map(LoanRecord::getLoanTypeId).orElse(null));
                  return LoanRepaymentClaimDto.from(
                      c, names.getOrDefault(c.getMemberId(), "Unknown member"),
                      typeId == null ? "Loan" : typeNames.getOrDefault(typeId, "Loan"));
                })
            .toList());
  }

  public record ClaimDecisionRequest(
      @NotBlank @Pattern(regexp = "Confirmed|Rejected", message = "Confirm or reject the transfer") String decision,
      @Size(max = 500, message = "Keep the reason under 500 characters") String reason) {}

  /** Confirming counts the payment, dated the day the member paid; rejecting tells them why. */
  @PatchMapping("/api/v1/cooperatives/{id}/loans/repayment-claims/{claimId}")
  @Transactional
  public ResponseEntity<?> decideClaim(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String claimId,
      @Valid @RequestBody ClaimDecisionRequest request,
      HttpServletRequest httpRequest) {
    var staff = requireLoanWriter(authentication, id, true);
    if (staff.error() != null) return staff.error();
    Member caller = staff.caller();

    LoanRepaymentClaim claim;
    try {
      claim = claimRepository.findById(UUID.fromString(claimId)).orElse(null);
    } catch (IllegalArgumentException e) {
      claim = null;
    }
    if (claim == null || !id.equals(claim.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that bank transfer"));
    }
    if (!LoanRepaymentClaim.PENDING.equals(claim.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That bank transfer was already " + claim.getStatus().toLowerCase()));
    }
    if (caller.getId().equals(claim.getMemberId())) {
      return ResponseEntity.status(403).body(Map.of("error", "Someone else has to confirm your own repayment"));
    }
    LoanRecord loan = loanRecordRepository.findById(claim.getLoanId()).orElse(null);
    if (loan == null) return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
    String typeName = typeNameOf(loan);

    if ("Rejected".equals(request.decision())) {
      claim.reject(caller.getFullName(), request.reason());
      claimRepository.save(claim);
      notificationService.notify(
          claim.getMemberId(),
          "LOAN_TRANSFER_REPAYMENT_REJECTED",
          "Your bank transfer wasn't confirmed",
          "The " + Amounts.naira(claim.getAmount()) + " bank transfer you submitted for your " + typeName
              + " loan wasn't confirmed." + (claim.getRejectionReason() != null ? " Reason: " + claim.getRejectionReason() : "")
              + " It hasn't been counted towards your loan.",
          "/loans/" + loan.getId());
    } else {
      ResponseEntity<?> notDue = requireOwing(loan);
      if (notDue != null) return notDue;
      BigDecimal owed = owed(loan);
      if (claim.getAmount().compareTo(owed) > 0) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "Only " + Amounts.naira(owed) + " is still owed on this loan. Reject this transfer and ask the member to submit the right amount."));
      }
      LoanRepayment repayment =
          recordPayment(loan, id, loan.getMemberId(), claim.getAmount(), "Bank Transfer",
              "BT-" + claim.getId().toString().substring(0, 8).toUpperCase());
      repayment.setManualProof(claim.getReceiptUrl(), caller.getFullName(), claim.getNote());
      repayment.setRepaymentDate(claim.getPaidOn());
      loanRepaymentRepository.save(repayment);
      claim.confirm(caller.getFullName());
      claimRepository.save(claim);
      notificationService.notify(
          claim.getMemberId(),
          "LOAN_TRANSFER_REPAYMENT_CONFIRMED",
          "Your bank transfer was confirmed",
          "Your " + Amounts.naira(claim.getAmount()) + " bank transfer was confirmed and counted towards your " + typeName
              + " loan." + ("Completed".equals(loan.getStatus())
                  ? " Your loan is now fully repaid."
                  : " " + Amounts.naira(owed(loan)) + " is left to pay."),
          "/loans/" + loan.getId());
    }
    auditLogService.log(
        caller.getId(), caller.getRole(), "Loans", "Update", "Transfer repayment",
        "Rejected".equals(request.decision()) ? "Warning" : "Success", httpRequest);
    Member borrower = memberRepository.findById(claim.getMemberId()).orElse(null);
    return ResponseEntity.ok(
        LoanRepaymentClaimDto.from(claim, borrower != null ? borrower.getFullName() : "Unknown member", typeName));
  }

  /* ─────────────────────────────── Bulk upload ─────────────────────────────── */

  public record BulkRepaymentRow(
      Integer row, String memberId, String loanType, String loanId, BigDecimal amount, String date) {}

  public record BulkRepaymentRequest(@NotNull List<BulkRepaymentRow> rows, boolean dryRun) {}

  public record BulkRowError(int row, String message) {}

  /** {@code monthsCovered} is how much of the schedule the row pays, e.g. 1.5. */
  public record BulkRepaymentLine(
      int row, String memberId, String memberName, String loanType, String loanId, BigDecimal amount,
      BigDecimal monthsCovered, BigDecimal outstandingAfter, LocalDate date, boolean completesLoan) {}

  public record BulkRepaymentResult(
      boolean dryRun, int imported, BigDecimal totalAmount, List<BulkRepaymentLine> lines, List<BulkRowError> errors) {}

  /**
   * Repayments for many loans at once — any amounts up to what each loan still owes. Every row
   * is checked first; if any is wrong, nothing is saved.
   */
  @PostMapping("/api/v1/cooperatives/{id}/loans/repayments/bulk")
  @Transactional
  public ResponseEntity<?> bulkRepayments(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody BulkRepaymentRequest request,
      HttpServletRequest httpRequest) {
    var staff = requireLoanWriter(authentication, id, true);
    if (staff.error() != null) return staff.error();
    if (request.rows().isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "The file has no rows to upload"));
    }
    if (request.rows().size() > 2000) {
      return ResponseEntity.badRequest().body(Map.of("error", "Upload at most 2,000 rows at a time"));
    }

    Map<String, Member> members = new HashMap<>();
    for (Member m : memberRepository.findAllByCooperativeId(id)) members.put(m.getId().toUpperCase(), m);
    Map<UUID, String> typeNames = new HashMap<>();
    for (LoanType t : loanTypeRepository.findAllByCooperativeId(id)) typeNames.put(t.getId(), t.getName());
    Map<String, List<LoanRecord>> activeByMember = new HashMap<>();
    for (LoanRecord l : loanRecordRepository.findAllByCooperativeIdAndStatus(id, "Active")) {
      activeByMember.computeIfAbsent(l.getMemberId().toUpperCase(), k -> new ArrayList<>()).add(l);
    }

    // Rows for the same loan follow on from each other.
    Map<UUID, BigDecimal> paid = new HashMap<>();
    List<BulkRowError> errors = new ArrayList<>();
    List<BulkRepaymentLine> lines = new ArrayList<>();
    List<Object[]> plan = new ArrayList<>(); // {LoanRecord, amount, date, rowNumber}
    int index = 0;
    for (BulkRepaymentRow row : request.rows()) {
      index++;
      int rowNumber = row.row() != null ? row.row() : index + 1;
      String memberId = row.memberId() == null ? "" : row.memberId().trim();
      if (memberId.isEmpty()) {
        errors.add(new BulkRowError(rowNumber, "Membership ID is missing"));
        continue;
      }
      Member member = members.get(memberId.toUpperCase());
      if (member == null) {
        errors.add(new BulkRowError(rowNumber, "No member " + memberId + " in this co-operative"));
        continue;
      }
      List<LoanRecord> candidates = new ArrayList<>(activeByMember.getOrDefault(memberId.toUpperCase(), List.of()));
      String loanIdText = row.loanId() == null ? "" : row.loanId().trim();
      String typeText = row.loanType() == null ? "" : row.loanType().trim();
      if (!loanIdText.isEmpty()) {
        candidates.removeIf(l -> !l.getId().toString().equalsIgnoreCase(loanIdText));
      } else if (!typeText.isEmpty()) {
        candidates.removeIf(l -> !typeText.equalsIgnoreCase(typeNames.getOrDefault(l.getLoanTypeId(), "")));
      }
      if (candidates.isEmpty()) {
        errors.add(new BulkRowError(rowNumber, member.getFullName() + " has no active "
            + (typeText.isEmpty() || !loanIdText.isEmpty() ? "" : typeText + " ") + "loan"
            + (loanIdText.isEmpty() ? "" : " with that Loan ID")));
        continue;
      }
      if (candidates.size() > 1) {
        errors.add(new BulkRowError(rowNumber, member.getFullName() + " has " + candidates.size()
            + " active loans" + (typeText.isEmpty() ? "; add the Loan Type" : " of this type; add the Loan ID")));
        continue;
      }
      LoanRecord loan = candidates.get(0);
      if (row.amount() == null || row.amount().signum() <= 0) {
        errors.add(new BulkRowError(rowNumber, "Amount must be more than zero"));
        continue;
      }
      LocalDate date;
      try {
        date = row.date() == null || row.date().isBlank() ? LocalDate.now() : LocalDate.parse(row.date().trim());
      } catch (DateTimeParseException e) {
        errors.add(new BulkRowError(rowNumber, "Date must be written as YYYY-MM-DD"));
        continue;
      }
      if (date.isAfter(LocalDate.now())) {
        errors.add(new BulkRowError(rowNumber, "Date can't be in the future"));
        continue;
      }
      BigDecimal paidSoFar = paid.computeIfAbsent(loan.getId(), k -> LoanBalances.repaid(loan));
      BigDecimal owed = loan.getTotalRepayment().subtract(paidSoFar).max(BigDecimal.ZERO);
      BigDecimal amount = row.amount().setScale(2, RoundingMode.HALF_UP);
      if (owed.signum() <= 0) {
        errors.add(new BulkRowError(rowNumber, "This loan is already fully repaid by the rows above"));
        continue;
      }
      if (amount.compareTo(owed) > 0) {
        errors.add(new BulkRowError(rowNumber, Amounts.naira(amount) + " is more than the " + Amounts.naira(owed)
            + " still owed on " + member.getFullName() + "'s loan"));
        continue;
      }
      BigDecimal after = paidSoFar.add(amount);
      lines.add(
          new BulkRepaymentLine(
              rowNumber, member.getId(), member.getFullName(), typeNames.getOrDefault(loan.getLoanTypeId(), "Loan"),
              loan.getId().toString(), amount, monthsCovered(loan, paidSoFar, amount),
              loan.getTotalRepayment().subtract(after).max(BigDecimal.ZERO), date,
              after.compareTo(loan.getTotalRepayment()) >= 0));
      plan.add(new Object[] {loan, amount, date, rowNumber});
      paid.put(loan.getId(), after);
    }

    BigDecimal total = lines.stream().map(BulkRepaymentLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (request.dryRun() || !errors.isEmpty()) {
      return ResponseEntity.status(request.dryRun() || errors.isEmpty() ? 200 : 422)
          .body(new BulkRepaymentResult(true, 0, total, lines, errors));
    }

    String batch = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    for (Object[] step : plan) {
      LoanRecord loan = (LoanRecord) step[0];
      LoanRepayment repayment =
          recordPayment(loan, id, loan.getMemberId(), (BigDecimal) step[1], "Bulk Upload", "BULKRP-" + batch + "-" + step[3]);
      repayment.setManualProof(null, staff.caller().getFullName(), null);
      repayment.setRepaymentDate((LocalDate) step[2]);
      loanRepaymentRepository.save(repayment);
    }
    auditLogService.log(
        staff.caller().getId(), staff.caller().getRole(), "Loans", "Import", lines.size() + " repayment rows", "Success", httpRequest);
    return ResponseEntity.ok(new BulkRepaymentResult(false, lines.size(), total, lines, List.of()));
  }

  /* ─────────────────────────────── The arithmetic ─────────────────────────────── */

  /**
   * Records one payment: a repayment row, the loan's running total (and so its installments and
   * completion), and for a Within loan the principal part going back into the member's savings.
   */
  private LoanRepayment recordPayment(
      LoanRecord loan, String cooperativeId, String memberId, BigDecimal amount, String method, String transactionId) {
    BigDecimal before = LoanBalances.repaid(loan);
    int installment = Math.min(LoanBalances.installmentsCovered(loan, before) + 1, loan.getNumberOfRepayments());
    LoanRepayment repayment =
        new LoanRepayment(loan.getId(), cooperativeId, memberId, installment, amount, method, transactionId);
    loanRepaymentRepository.save(repayment);

    loan.applyRepayment(before, amount);
    loanRecordRepository.save(loan);

    if (loan.isWithin()) {
      // Borrowed from the member's own savings: each payment's principal share goes back in.
      BigDecimal principal =
          "Completed".equals(loan.getStatus())
              ? loan.getAmount().subtract(LoanBalances.principalIn(loan, before))
              : LoanBalances.principalIn(loan, before.add(amount)).subtract(LoanBalances.principalIn(loan, before));
      if (principal.signum() > 0) {
        BigDecimal balanceAfter = savingsRecordRepository.sumByMember(memberId).add(principal);
        savingsRecordRepository.save(
            new SavingsRecord(
                cooperativeId, memberId, null, principal, balanceAfter, "Loan Repayment", transactionId, "Success", null));
      }
    }
    return repayment;
  }

  /** What takes the loan from {@code paid} to the end of installment {@code k}. */
  private static BigDecimal amountToClear(LoanRecord loan, BigDecimal paid, int k) {
    return LoanBalances.cumulativeAt(loan, Math.min(k, loan.getNumberOfRepayments()))
        .subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  /** How many months of the schedule {@code amount} pays, starting from {@code paid} (e.g. 1.50). */
  static BigDecimal monthsCovered(LoanRecord loan, BigDecimal paid, BigDecimal amount) {
    return monthsAt(loan, paid.add(amount)).subtract(monthsAt(loan, paid)).setScale(2, RoundingMode.HALF_UP);
  }

  /** Months of the schedule {@code paid} amounts to, counting part of an installment as part of a month. */
  private static BigDecimal monthsAt(LoanRecord loan, BigDecimal paid) {
    int n = loan.getNumberOfRepayments();
    int covered = LoanBalances.installmentsCovered(loan, paid);
    if (covered >= n) return BigDecimal.valueOf(n);
    BigDecimal start = LoanBalances.cumulativeAt(loan, covered);
    BigDecimal size = LoanBalances.cumulativeAt(loan, covered + 1).subtract(start);
    if (size.signum() <= 0) return BigDecimal.valueOf(covered);
    return BigDecimal.valueOf(covered).add(paid.subtract(start).divide(size, 4, RoundingMode.HALF_UP));
  }

  private static BigDecimal owed(LoanRecord loan) {
    return loan.getTotalRepayment().subtract(LoanBalances.repaid(loan)).max(BigDecimal.ZERO);
  }

  private record AmountCheck(BigDecimal amount, ResponseEntity<?> error) {}

  /** A payment's amount: given, it must be more than zero and no more than what's owed;
   * left out, it's what finishes the current installment. */
  private AmountCheck checkAmount(LoanRecord loan, BigDecimal requested, BigDecimal paid) {
    ResponseEntity<?> notDue = requireOwing(loan);
    if (notDue != null) return new AmountCheck(null, notDue);
    BigDecimal owed = loan.getTotalRepayment().subtract(paid).max(BigDecimal.ZERO);
    BigDecimal amount =
        requested == null
            ? amountToClear(loan, paid, LoanBalances.installmentsCovered(loan, paid) + 1)
            : requested.setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() <= 0) {
      return new AmountCheck(null, ResponseEntity.badRequest().body(Map.of("error", "Enter an amount more than zero")));
    }
    if (amount.compareTo(owed) > 0) {
      return new AmountCheck(
          null,
          ResponseEntity.badRequest()
              .body(Map.of("error", "Only " + Amounts.naira(owed) + " is left to pay on this loan. Enter that or less.")));
    }
    return new AmountCheck(amount, null);
  }

  private static ResponseEntity<?> requireOwing(LoanRecord loan) {
    if (!"Active".equals(loan.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan isn't active"));
    }
    if (LoanBalances.repaid(loan).compareTo(loan.getTotalRepayment()) >= 0) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan is already fully repaid"));
    }
    return null;
  }

  private LoanRepaymentResultDto toResult(LoanRepayment repayment, LoanRecord loan) {
    Member member = memberRepository.findById(loan.getMemberId()).orElse(null);
    LoanType type = loanTypeRepository.findById(loan.getLoanTypeId()).orElse(null);
    return new LoanRepaymentResultDto(
        LoanRepaymentDto.from(repayment),
        LoanRecordDto.from(
            loan, member != null ? member.getFullName() : "Unknown member", type != null ? type.getName() : "Unknown type"));
  }

  private String typeNameOf(LoanRecord loan) {
    return loanTypeRepository.findById(loan.getLoanTypeId()).map(LoanType::getName).orElse("Loan");
  }

  private LoanRecord findLoan(String id) {
    try {
      return loanRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /* ─────────────────────────────── Who can do what ─────────────────────────────── */

  private record LoanAccess(Member caller, LoanRecord loan, ResponseEntity<?> error) {}

  /** Strictly the loan's own borrower (or super admin) — only the person who owes may pay. */
  private LoanAccess requireBorrower(Authentication authentication, String cooperativeId, String loanId) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return new LoanAccess(null, null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    LoanRecord loan = findLoan(loanId);
    if (loan == null || !cooperativeId.equals(loan.getCooperativeId())) {
      return new LoanAccess(null, null, ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan")));
    }
    if (!"super_admin".equals(caller.getRole()) && !caller.getId().equals(loan.getMemberId())) {
      return new LoanAccess(null, null, ResponseEntity.status(403).body(Map.of("error", "Only this loan's borrower can repay it")));
    }
    return new LoanAccess(caller, loan, null);
  }

  /** The borrower, a guarantor, staff of the co-op, or a super admin — read-only lookups. */
  private LoanAccess requireLoanParty(Authentication authentication, String cooperativeId, String loanId) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
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
      return new LoanAccess(null, null, ResponseEntity.status(403).body(Map.of("error", "You can't view that loan")));
    }
    return new LoanAccess(caller, loan, null);
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

  private CoopAccess requireCoopAccess(Authentication authentication, String cooperativeId) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) return new CoopAccess(caller, null);
    boolean isCoopStaff = "admin".equals(caller.getRole()) || caller.getCoopRoleId() != null;
    if (isCoopStaff && cooperativeId.equals(caller.getCooperativeId())) return new CoopAccess(caller, null);
    return new CoopAccess(
        null, ResponseEntity.status(403).body(Map.of("error", "You can only manage your own co-operative's loans")));
  }

  /** The co-op admin, staff with access to Loans → Members Loans (write to act), or a super admin. */
  private CoopAccess requireLoanWriter(Authentication authentication, String cooperativeId, boolean write) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())
        || coopPermissions.allows(caller, cooperativeId, "Loans", "Members Loans", write)) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null, ResponseEntity.status(403).body(Map.of("error", "You don't have permission to manage members' loan repayments")));
  }
}
