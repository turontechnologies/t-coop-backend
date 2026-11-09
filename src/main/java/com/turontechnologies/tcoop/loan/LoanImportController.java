package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin import of members' existing loans (Loans -> Members Loans -> Import). Each call brings in
 * one loan that was already paid out offline: it's created Active (Completed once every
 * installment is paid), skips the guarantor/officer chain and the Paystack payout, and gets a
 * repayment row for each installment already paid so balances and history stay right. It never
 * touches the savings ledger — an imported Within loan's savings are assumed already correct.
 *
 * <p>Allowed for the co-op admin and co-op staff with write access to Loans -> Members Loans.
 */
@RestController
public class LoanImportController {

  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanRepaymentRepository loanRepaymentRepository;
  private final LoanGuarantorRepository loanGuarantorRepository;
  private final CoopPermissions coopPermissions;
  private final AuditLogService auditLogService;

  public LoanImportController(
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanRepaymentRepository loanRepaymentRepository,
      LoanGuarantorRepository loanGuarantorRepository,
      CoopPermissions coopPermissions,
      AuditLogService auditLogService) {
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanRepaymentRepository = loanRepaymentRepository;
    this.loanGuarantorRepository = loanGuarantorRepository;
    this.coopPermissions = coopPermissions;
    this.auditLogService = auditLogService;
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/import")
  @Transactional
  public ResponseEntity<?> importLoan(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody LoanImportRequest request,
      HttpServletRequest httpRequest) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!coopPermissions.allows(caller, id, "Loans", "Members Loans", true)) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "You don't have permission to import members' loans"));
    }

    Member borrower = memberRepository.findById(request.memberId().trim()).orElse(null);
    if (borrower == null || !id.equals(borrower.getCooperativeId())) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "No member " + request.memberId().trim() + " in this co-operative"));
    }

    LoanType type = findType(request.loanTypeId(), id);
    if (type == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan type"));
    }

    String mode = request.loanMode() == null || request.loanMode().isBlank()
        ? LoanEligibility.TIMES_TWO
        : request.loanMode();
    if (!LoanEligibility.isValidMode(mode)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Loan Mode must be \"TimesTwo\" or \"Within\""));
    }

    // Optional for an imported loan (older records may not have one), but when given it has to be
    // another member of this co-op.
    Member guarantor = null;
    if (!LoanEligibility.WITHIN.equals(mode)
        && request.guarantorMemberId() != null
        && !request.guarantorMemberId().isBlank()) {
      guarantor = memberRepository.findById(request.guarantorMemberId().trim()).orElse(null);
      if (guarantor == null || !id.equals(guarantor.getCooperativeId())) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "The guarantor must be a member of this co-operative"));
      }
      if (guarantor.getId().equals(borrower.getId())) {
        return ResponseEntity.badRequest().body(Map.of("error", "A member can't guarantee their own loan"));
      }
    }

    LocalDate start;
    try {
      start = request.startDate() == null || request.startDate().isBlank()
          ? LocalDate.now()
          : LocalDate.parse(request.startDate().trim());
    } catch (DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Start Date must be YYYY-MM-DD"));
    }
    if (start.isAfter(LocalDate.now())) {
      return ResponseEntity.badRequest().body(Map.of("error", "Start Date can't be in the future"));
    }

    // Same terms the loan would have had if applied for today.
    BigDecimal amount = request.amount();
    BigDecimal totalInterest = LoanEligibility.totalInterest(type, amount);
    BigDecimal interestRate =
        totalInterest.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP);
    BigDecimal totalRepayment = amount.add(totalInterest);
    int installments = type.getDurationMonths();
    BigDecimal monthly =
        totalRepayment.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);

    int paid = request.repaymentsMade() == null ? 0 : request.repaymentsMade();
    if (paid > installments) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Repayments Made can't be more than this loan type's " + installments + " installments"));
    }

    LoanRecord record =
        new LoanRecord(
            id,
            borrower.getId(),
            type.getId(),
            amount,
            interestRate,
            type.getDurationMonths(),
            installments,
            monthly,
            totalRepayment,
            guarantor != null ? guarantor.getId() : null,
            guarantor != null ? guarantor.getFullName() : null,
            "Active",
            mode);
    record.markImported(start, paid, paid == installments ? "Completed" : "Active");
    loanRecordRepository.save(record);
    if (guarantor != null) {
      loanGuarantorRepository.save(
          new LoanGuarantor(record.getId(), guarantor.getId(), guarantor.getFullName(), LoanGuarantor.ACCEPTED));
    }

    for (int n = 1; n <= paid; n++) {
      // The last installment absorbs the rounding so a fully repaid loan sums to the total exactly.
      BigDecimal installmentAmount =
          n == installments
              ? totalRepayment.subtract(monthly.multiply(BigDecimal.valueOf(installments - 1L)))
              : monthly;
      LoanRepayment repayment =
          new LoanRepayment(
              record.getId(),
              id,
              borrower.getId(),
              n,
              installmentAmount,
              "Manual Upload", // offline money; the IMPORT- transaction id marks it as imported

              "IMPORT-" + record.getId() + "-" + n);
      LocalDate due = LoanSchedule.dueDate(record, n);
      repayment.setRepaymentDate(due.isAfter(LocalDate.now()) ? LocalDate.now() : due);
      loanRepaymentRepository.save(repayment);
    }

    auditLogService.log(
        caller.getId(), caller.getRole(), "Loans", "Import", borrower.getFullName(), "Success", httpRequest);

    return ResponseEntity.ok(LoanRecordDto.from(record, borrower.getFullName(), type.getName()));
  }

  private LoanType findType(String typeId, String coopId) {
    try {
      return loanTypeRepository
          .findById(UUID.fromString(typeId))
          .filter(type -> coopId.equals(type.getCooperativeId()))
          .orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
