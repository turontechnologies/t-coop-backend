package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.notification.Amounts;
import com.turontechnologies.tcoop.notification.NotificationService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsRecord;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
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
 * A borrower's own loan-installment repayment (online initialize/confirm through the co-op's
 * gateway — see CoopGatewayResolver — mirroring
 * {@code SavingsSelfServiceController}'s deposit pattern exactly), and an admin/coop-staff's
 * manual entry after receiving payment offline. Every repayment pays exactly one fixed
 * installment — this app doesn't support partial or arbitrary-amount repayments, so the amount is
 * always computed server-side from the loan's own terms, never client-supplied.
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

  /** What's due next — the borrower's own "Make a Repayment" button and the admin's "Record
   * Repayment" button both call this first to know the exact amount before submitting. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/next")
  public ResponseEntity<?> next(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @RequestParam(required = false) Integer count) {
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
    int remaining = loan.getNumberOfRepayments() - loan.getRepaymentsMade();
    int n = count == null ? 1 : Math.max(1, Math.min(count, remaining));
    List<BigDecimal> amounts =
        installmentAmounts(loan, loan.getRepaymentsMade(), loanRepaymentRepository.sumByLoan(loan.getId()), n);
    return ResponseEntity.ok(
        new NextInstallmentDto(
            installmentNumber,
            sum(amounts),
            n,
            remaining,
            amounts.get(0),
            claimRepository.existsByLoanIdAndStatus(loan.getId(), LoanRepaymentClaim.PENDING)));
  }

  /* ──────────────────── Repaid by bank transfer: member submits, co-op confirms ──────────────────── */

  public record TransferClaimRequest(
      Integer installments,
      String receiptUrl,
      @jakarta.validation.constraints.Size(max = 100) String transferReference,
      String paidOn,
      @jakarta.validation.constraints.Size(max = 500, message = "Keep the note under 500 characters") String note) {}

  /**
   * The borrower paid by bank transfer outside the platform and uploads the receipt. Nothing
   * counts yet: the co-op confirms it first (see {@link #decideClaim}). Whole installments only;
   * the amount is worked out here, never taken from the member.
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
    var dueCheck = requireDue(loan);
    if (dueCheck.error() != null) return dueCheck.error();
    if (claimRepository.existsByLoanIdAndStatus(loan.getId(), LoanRepaymentClaim.PENDING)) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "You already have a bank transfer waiting to be confirmed for this loan."));
    }

    String receiptUrl = request.receiptUrl() == null ? "" : request.receiptUrl().trim();
    if (!receiptUrl.startsWith("https://") && !receiptUrl.startsWith("http://")) {
      return ResponseEntity.badRequest().body(Map.of("error", "Upload the transfer receipt first."));
    }
    int remaining = loan.getNumberOfRepayments() - loan.getRepaymentsMade();
    int count = request.installments() == null ? 1 : request.installments();
    if (count < 1 || count > remaining) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Choose between 1 and " + remaining + " installment" + (remaining == 1 ? "" : "s")));
    }
    LocalDate paidOn;
    try {
      paidOn = request.paidOn() == null || request.paidOn().isBlank() ? LocalDate.now() : LocalDate.parse(request.paidOn().trim());
    } catch (DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Choose the date you made the transfer"));
    }
    if (paidOn.isAfter(LocalDate.now())) {
      return ResponseEntity.badRequest().body(Map.of("error", "The transfer date can't be in the future"));
    }

    BigDecimal amount =
        sum(installmentAmounts(loan, loan.getRepaymentsMade(), loanRepaymentRepository.sumByLoan(loan.getId()), count));
    LoanRepaymentClaim claim =
        new LoanRepaymentClaim(
            loan.getId(), id, loan.getMemberId(), count, amount, receiptUrl, request.transferReference(), paidOn, request.note());
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
    return ResponseEntity.ok(toClaimDto(claim, access.caller().getFullName(), typeName));
  }

  /** One loan's bank-transfer submissions: the borrower, guarantors and staff can see them. */
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
            .map(c -> toClaimDto(c, name, typeName))
            .toList());
  }

  /** The co-op's queue of bank-transfer repayments, newest first ({@code status=Pending} for the to-do list). */
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
                          c.getLoanId(), loanKey -> loanRecordRepository.findById(loanKey).map(LoanRecord::getLoanTypeId).orElse(null));
                  return toClaimDto(
                      c, names.getOrDefault(c.getMemberId(), "Unknown member"),
                      typeId == null ? "Loan" : typeNames.getOrDefault(typeId, "Loan"));
                })
            .toList());
  }

  public record ClaimDecisionRequest(
      @jakarta.validation.constraints.NotBlank
          @jakarta.validation.constraints.Pattern(regexp = "Confirmed|Rejected", message = "Confirm or reject the transfer")
          String decision,
      @jakarta.validation.constraints.Size(max = 500, message = "Keep the reason under 500 characters") String reason) {}

  /**
   * The admin (or loan staff) checked their bank. Confirming records the installments, dated the
   * day the member paid, so the balance and schedule count them; rejecting tells the member why.
   */
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
      var dueCheck = requireDue(loan);
      if (dueCheck.error() != null) return dueCheck.error();
      int remaining = loan.getNumberOfRepayments() - loan.getRepaymentsMade();
      if (claim.getInstallments() > remaining) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "This loan only has " + remaining + " installment" + (remaining == 1 ? "" : "s")
                + " left. Reject this transfer and ask the member to submit it again."));
      }
      List<BigDecimal> amounts =
          installmentAmounts(loan, loan.getRepaymentsMade(), loanRepaymentRepository.sumByLoan(loan.getId()), claim.getInstallments());
      String base = "BT-" + claim.getId().toString().substring(0, 8).toUpperCase();
      for (int k = 0; k < amounts.size(); k++) {
        int installment = loan.getRepaymentsMade() + 1;
        LoanRepayment repayment =
            recordRepayment(loan, id, loan.getMemberId(), installment, amounts.get(k), "Bank Transfer", base + "-" + installment);
        repayment.setManualProof(claim.getReceiptUrl(), caller.getFullName(), claim.getNote());
        repayment.setRepaymentDate(claim.getPaidOn());
        loanRepaymentRepository.save(repayment);
      }
      claim.confirm(caller.getFullName());
      claimRepository.save(claim);
      notificationService.notify(
          claim.getMemberId(),
          "LOAN_TRANSFER_REPAYMENT_CONFIRMED",
          "Your bank transfer was confirmed",
          "Your " + Amounts.naira(sum(amounts)) + " bank transfer was confirmed and counted towards your " + typeName
              + " loan." + ("Completed".equals(loan.getStatus()) ? " Your loan is now fully repaid." : ""),
          "/loans/" + loan.getId());
    }
    auditLogService.log(
        caller.getId(), caller.getRole(), "Loans", "Update", "Transfer repayment",
        "Rejected".equals(request.decision()) ? "Warning" : "Success", httpRequest);
    Member borrower = memberRepository.findById(claim.getMemberId()).orElse(null);
    return ResponseEntity.ok(toClaimDto(claim, borrower != null ? borrower.getFullName() : "Unknown member", typeName));
  }

  /* ─────────────────────────────── Bulk upload ─────────────────────────────── */

  public record BulkRepaymentRow(
      Integer row, String memberId, String loanType, String loanId, BigDecimal amount, String date) {}

  public record BulkRepaymentRequest(@jakarta.validation.constraints.NotNull List<BulkRepaymentRow> rows, boolean dryRun) {}

  public record BulkRowError(int row, String message) {}

  public record BulkRepaymentLine(
      int row, String memberId, String memberName, String loanType, String loanId, BigDecimal amount,
      int installments, int fromInstallment, int totalInstallments, LocalDate date, boolean completesLoan) {}

  public record BulkRepaymentResult(
      boolean dryRun, int imported, BigDecimal totalAmount, List<BulkRepaymentLine> lines, List<BulkRowError> errors) {}

  /**
   * Loan repayments for many members at once — Loans → Bulk upload. Each row's amount has to be
   * a whole number of the loan's next installments (that's the only kind of repayment this app
   * has). Every row is checked first; if any is wrong, nothing is saved.
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

    // Rows for the same loan follow on from each other, so track where each loan would be.
    Map<UUID, int[]> made = new HashMap<>();
    Map<UUID, BigDecimal> paid = new HashMap<>();
    List<BulkRowError> errors = new ArrayList<>();
    List<BulkRepaymentLine> lines = new ArrayList<>();
    List<Object[]> plan = new ArrayList<>(); // {LoanRecord, List<BigDecimal>, LocalDate, rowNumber}
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
            + (typeText.isEmpty() || !loanIdText.isEmpty() ? "" : typeText + " ") + "loan" + (loanIdText.isEmpty() ? "" : " with that Loan ID")));
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

      int[] madeSoFar = made.computeIfAbsent(loan.getId(), k -> new int[] {loan.getRepaymentsMade()});
      BigDecimal paidSoFar = paid.computeIfAbsent(loan.getId(), k -> loanRepaymentRepository.sumByLoan(k));
      int remaining = loan.getNumberOfRepayments() - madeSoFar[0];
      if (remaining <= 0) {
        errors.add(new BulkRowError(rowNumber, "This loan is already fully repaid by the rows above"));
        continue;
      }
      List<BigDecimal> all = installmentAmounts(loan, madeSoFar[0], paidSoFar, remaining);
      BigDecimal target = row.amount().setScale(2, RoundingMode.HALF_UP);
      BigDecimal running = BigDecimal.ZERO;
      int count = 0;
      while (count < all.size() && running.compareTo(target) < 0) {
        running = running.add(all.get(count));
        count++;
      }
      if (running.compareTo(target) != 0) {
        errors.add(new BulkRowError(rowNumber, Amounts.naira(target) + " isn't a whole number of installments. "
            + "The next installment is " + Amounts.naira(all.get(0))
            + (all.size() > 1 ? " (" + Amounts.naira(sum(all.subList(0, 2))) + " for two)" : "")
            + " and " + Amounts.naira(sum(all)) + " clears the loan."));
        continue;
      }
      List<BigDecimal> amounts = all.subList(0, count);
      lines.add(
          new BulkRepaymentLine(
              rowNumber, member.getId(), member.getFullName(), typeNames.getOrDefault(loan.getLoanTypeId(), "Loan"),
              loan.getId().toString(), target, count, madeSoFar[0] + 1, loan.getNumberOfRepayments(), date,
              count == remaining));
      plan.add(new Object[] {loan, new ArrayList<>(amounts), date, rowNumber});
      madeSoFar[0] += count;
      paid.put(loan.getId(), paidSoFar.add(target));
    }

    BigDecimal total = lines.stream().map(BulkRepaymentLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (request.dryRun() || !errors.isEmpty()) {
      return ResponseEntity.status(request.dryRun() || errors.isEmpty() ? 200 : 422)
          .body(new BulkRepaymentResult(true, 0, total, lines, errors));
    }

    String batch = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    for (Object[] step : plan) {
      LoanRecord loan = (LoanRecord) step[0];
      @SuppressWarnings("unchecked")
      List<BigDecimal> amounts = (List<BigDecimal>) step[1];
      LocalDate date = (LocalDate) step[2];
      for (BigDecimal amount : amounts) {
        int installment = loan.getRepaymentsMade() + 1;
        LoanRepayment repayment =
            recordRepayment(loan, id, loan.getMemberId(), installment, amount, "Bulk Upload",
                "BULKRP-" + batch + "-" + step[3] + "-" + installment);
        repayment.setManualProof(null, staff.caller().getFullName(), null);
        repayment.setRepaymentDate(date);
        loanRepaymentRepository.save(repayment);
      }
    }
    auditLogService.log(
        staff.caller().getId(), staff.caller().getRole(), "Loans", "Import", lines.size() + " repayment rows", "Success", httpRequest);
    return ResponseEntity.ok(new BulkRepaymentResult(false, lines.size(), total, lines, List.of()));
  }

  /** The amounts of the next {@code count} installments after {@code made} have been paid
   * (totalling {@code paidSoFar}): the flat monthly amount, with the loan's final installment
   * taking whatever's left so a repaid loan adds up exactly. */
  private static List<BigDecimal> installmentAmounts(LoanRecord loan, int made, BigDecimal paidSoFar, int count) {
    List<BigDecimal> amounts = new ArrayList<>();
    BigDecimal running = paidSoFar;
    for (int k = 1; k <= count; k++) {
      int number = made + k;
      if (number > loan.getNumberOfRepayments()) break;
      BigDecimal amount =
          number < loan.getNumberOfRepayments()
              ? loan.getMonthlyRepayment().setScale(2, RoundingMode.HALF_UP)
              : loan.getTotalRepayment().subtract(running).setScale(2, RoundingMode.HALF_UP);
      amounts.add(amount);
      running = running.add(amount);
    }
    return amounts;
  }

  private static BigDecimal sum(List<BigDecimal> amounts) {
    return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String typeNameOf(LoanRecord loan) {
    return loanTypeRepository.findById(loan.getLoanTypeId()).map(LoanType::getName).orElse("Loan");
  }

  private LoanRepaymentClaimDto toClaimDto(LoanRepaymentClaim claim, String memberName, String typeName) {
    return LoanRepaymentClaimDto.from(claim, memberName, typeName);
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

  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/initialize")
  public ResponseEntity<?> initialize(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireBorrower(authentication, id, loanId);
    if (access.error() != null) return access.error();

    LoanRecord loan = access.loan();
    var dueCheck = requireDue(loan);
    if (dueCheck.error() != null) return dueCheck.error();

    CoopGatewayResolver.Checkout checkout = gatewayResolver.checkoutFor(id).orElse(null);
    if (checkout == null) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "Online payments aren't set up for this co-operative yet."));
    }

    String reference = "LNRPY-" + id + "-" + System.currentTimeMillis();
    intentRepository.save(
        new LoanPaymentIntent(
            reference,
            loan.getId(),
            id,
            access.caller().getId(),
            dueCheck.installmentNumber(),
            dueCheck.amount(),
            checkout.gateway(),
            checkout.keyOwner()));

    return ResponseEntity.ok(
        new InitializeLoanRepaymentResponseDto(
            reference, dueCheck.installmentNumber(), dueCheck.amount(), checkout.gateway(), checkout.publicKey()));
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
      return ResponseEntity.status(402)
          .body(Map.of("error", "The amount paid doesn't match what was expected."));
    }

    intent.setStatus("Confirmed");
    intentRepository.save(intent);

    LoanRepayment repayment =
        recordRepayment(
            access.loan(), id, access.caller().getId(), intent.getInstallmentNumber(), intent.getAmount(),
            intent.getGateway(), intent.getReference());

    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Loans", "Update", "Repayment", "Success", httpRequest);

    return ResponseEntity.ok(toResult(repayment, access.loan()));
  }

  /** The receipt (required) and an optional note for a repayment recorded by hand. */
  public record ManualRepaymentRequest(
      String receiptUrl,
      @jakarta.validation.constraints.Size(max = 500, message = "Keep the note under 500 characters") String note) {}

  /** Admin/coop-staff's manual entry after receiving payment offline — no Paystack, and (like the
   * self-service side) no client-supplied amount; it's always the loan's own next fixed
   * installment. The receipt for the payment has to be uploaded with it. */
  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/repayments/manual")
  public ResponseEntity<?> manual(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody(required = false)
          ManualRepaymentRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    String receiptUrl = request == null || request.receiptUrl() == null ? "" : request.receiptUrl().trim();
    if (receiptUrl.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Upload the receipt for this payment before recording it."));
    }
    if (!receiptUrl.startsWith("https://") && !receiptUrl.startsWith("http://")) {
      return ResponseEntity.badRequest().body(Map.of("error", "That receipt upload didn't work. Please upload it again."));
    }

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
    repayment.setManualProof(receiptUrl, access.caller().getFullName(), request.note());
    loanRepaymentRepository.save(repayment);

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

    if (loan.isWithin()) {
      // A Within loan was borrowed from the member's own savings — the principal part of each
      // installment goes back into them; the interest part is the co-op's income.
      BigDecimal principal = principalOfInstallment(loan, installmentNumber);
      BigDecimal balanceAfter = savingsRecordRepository.sumByMember(memberId).add(principal);
      savingsRecordRepository.save(
          new SavingsRecord(
              cooperativeId, memberId, null, principal, balanceAfter, "Loan Repayment", transactionId, "Success", null));
    }
    return repayment;
  }

  /** The principal share of one installment: an even split, with the final installment taking
   * whatever rounding left over so the principal returned to savings adds up exactly. */
  private static BigDecimal principalOfInstallment(LoanRecord loan, int installmentNumber) {
    BigDecimal perInstallment =
        loan.getAmount().divide(BigDecimal.valueOf(loan.getNumberOfRepayments()), 2, RoundingMode.DOWN);
    if (installmentNumber < loan.getNumberOfRepayments()) return perInstallment;
    return loan.getAmount().subtract(perInstallment.multiply(BigDecimal.valueOf(loan.getNumberOfRepayments() - 1)));
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
