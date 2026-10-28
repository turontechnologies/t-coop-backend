package com.turontechnologies.tcoop.report;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.loan.LoanRecord;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.loan.LoanRepayment;
import com.turontechnologies.tcoop.loan.LoanRepaymentRepository;
import com.turontechnologies.tcoop.loan.LoanSchedule;
import com.turontechnologies.tcoop.loan.LoanType;
import com.turontechnologies.tcoop.loan.LoanTypeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsPaymentIntent;
import com.turontechnologies.tcoop.savings.SavingsPaymentIntentRepository;
import com.turontechnologies.tcoop.savings.SavingsRecord;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.savings.SavingsRequest;
import com.turontechnologies.tcoop.savings.SavingsRequestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A co-op's financial report for a date range — savings in and out, loans paid out and repaid,
 * income earned, what's outstanding and overdue — for the co-op admin and any staff whose role
 * grants Reports. Built from the same ledgers every other screen reads (savings_records,
 * loan_records, loan_repayments, savings_requests), so it always reconciles with them.
 */
@RestController
public class FinancialReportController {

  private static final Set<String> DEPOSIT_METHODS = Set.of("Paystack", "Flutterwave", "Manual Upload");

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final SavingsPaymentIntentRepository savingsIntentRepository;
  private final SavingsRequestRepository savingsRequestRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanRepaymentRepository loanRepaymentRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final CoopPermissions coopPermissions;

  public FinancialReportController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SavingsRecordRepository savingsRecordRepository,
      SavingsPaymentIntentRepository savingsIntentRepository,
      SavingsRequestRepository savingsRequestRepository,
      LoanRecordRepository loanRecordRepository,
      LoanRepaymentRepository loanRepaymentRepository,
      LoanTypeRepository loanTypeRepository,
      CoopPermissions coopPermissions) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.savingsIntentRepository = savingsIntentRepository;
    this.savingsRequestRepository = savingsRequestRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanRepaymentRepository = loanRepaymentRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.coopPermissions = coopPermissions;
  }

  @GetMapping("/api/v1/cooperatives/{id}/reports/financial")
  public ResponseEntity<?> financial(
      Authentication authentication,
      @PathVariable String id,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!coopPermissions.allows(caller, id, "Reports", null, false)) {
      return ResponseEntity.status(403).body(Map.of("error", "You don't have access to this co-operative's reports"));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    LocalDate today = LocalDate.now();
    LocalDate start;
    LocalDate end;
    try {
      start = from == null || from.isBlank() ? today.withDayOfYear(1) : LocalDate.parse(from);
      end = to == null || to.isBlank() ? today : LocalDate.parse(to);
    } catch (DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Dates must look like 2026-01-31"));
    }
    if (end.isBefore(start)) {
      return ResponseEntity.badRequest().body(Map.of("error", "The start date must be before the end date"));
    }

    Map<String, String> memberNames =
        memberRepository.findAllByCooperativeId(id).stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName, (a, b) -> a));
    Map<UUID, String> typeNames =
        loanTypeRepository.findAllByCooperativeId(id).stream()
            .collect(Collectors.toMap(LoanType::getId, LoanType::getName, (a, b) -> a));

    Map<String, MonthTotals> months = monthsBetween(start, end);

    // --- Savings ---
    List<SavingsRecord> savingsRecords =
        savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(r -> "Success".equals(r.getStatus()))
            .toList();
    BigDecimal deposits = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO;
    BigDecimal withinDebits = BigDecimal.ZERO, withinCredits = BigDecimal.ZERO, closing = BigDecimal.ZERO;
    int depositCount = 0, withdrawalCount = 0;
    List<SavingsRecord> onlineDeposits = new ArrayList<>();
    for (SavingsRecord record : savingsRecords) {
      LocalDate date = record.getRecordDate();
      if (!date.isAfter(end)) closing = closing.add(record.getAmount());
      if (!inRange(date, start, end)) continue;
      String method = record.getMethod();
      if ("Loan Disbursement".equals(method)) {
        withinDebits = withinDebits.add(record.getAmount().abs());
      } else if ("Loan Repayment".equals(method)) {
        withinCredits = withinCredits.add(record.getAmount());
      } else if (DEPOSIT_METHODS.contains(method) && record.getAmount().signum() > 0) {
        deposits = deposits.add(record.getAmount());
        depositCount++;
        months.get(key(date)).deposits = months.get(key(date)).deposits.add(record.getAmount());
        if (!"Manual Upload".equals(method)) onlineDeposits.add(record);
      } else if (record.getAmount().signum() < 0) {
        withdrawals = withdrawals.add(record.getAmount().abs());
        withdrawalCount++;
        months.get(key(date)).withdrawals = months.get(key(date)).withdrawals.add(record.getAmount().abs());
      }
    }

    // The savings charge on an online deposit is what the member paid minus what was credited.
    BigDecimal savingsCharges = BigDecimal.ZERO;
    Map<String, SavingsPaymentIntent> intents =
        savingsIntentRepository.findAllById(onlineDeposits.stream().map(SavingsRecord::getTransactionId).toList()).stream()
            .collect(Collectors.toMap(SavingsPaymentIntent::getReference, Function.identity(), (a, b) -> a));
    for (SavingsRecord record : onlineDeposits) {
      SavingsPaymentIntent intent = intents.get(record.getTransactionId());
      if (intent != null) savingsCharges = savingsCharges.add(intent.getAmount().subtract(record.getAmount()).max(BigDecimal.ZERO));
    }

    BigDecimal withdrawalFees = BigDecimal.ZERO;
    for (SavingsRequest request : savingsRequestRepository.findAllByCooperativeIdOrderByRequestedAtDesc(id)) {
      if ("Approved".equals(request.getStatus())
          && request.getResolvedAt() != null
          && inRange(request.getResolvedAt().toLocalDate(), start, end)
          && request.getFeeAmount() != null) {
        withdrawalFees = withdrawalFees.add(request.getFeeAmount());
      }
    }

    // --- Loans ---
    List<LoanRecord> loans = loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id);
    Map<UUID, LoanRecord> loansById = loans.stream().collect(Collectors.toMap(LoanRecord::getId, Function.identity()));
    List<LoanRepayment> repayments = loanRepaymentRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id);
    Map<UUID, BigDecimal> repaidByLoan = new HashMap<>();
    for (LoanRepayment repayment : repayments) {
      repaidByLoan.merge(repayment.getLoanId(), repayment.getAmount(), BigDecimal::add);
    }

    int applications = 0, paidOut = 0, rejected = 0, activeLoans = 0, overdueLoans = 0;
    BigDecimal principalPaidOut = BigDecimal.ZERO, netPaidOut = BigDecimal.ZERO, loanCharges = BigDecimal.ZERO;
    BigDecimal outstanding = BigDecimal.ZERO, overdueOutstanding = BigDecimal.ZERO;
    Map<String, TypeTotals> byType = new LinkedHashMap<>();
    List<FinancialReportDto.OverdueLoan> overdue = new ArrayList<>();

    for (LoanRecord loan : loans) {
      String typeName = typeNames.getOrDefault(loan.getLoanTypeId(), "Unknown type");
      TypeTotals typeTotals = byType.computeIfAbsent(typeName, k -> new TypeTotals());
      BigDecimal repaid = repaidByLoan.getOrDefault(loan.getId(), BigDecimal.ZERO);

      if (inRange(loan.getLoanDate(), start, end)) {
        applications++;
        if ("Rejected".equals(loan.getStatus())) rejected++;
      }
      LocalDate paidOn = loan.getDisbursedAt() != null ? loan.getDisbursedAt().toLocalDate() : null;
      if (paidOn != null && inRange(paidOn, start, end)) {
        paidOut++;
        principalPaidOut = principalPaidOut.add(loan.getAmount());
        BigDecimal net = loan.getDisbursedAmount() != null ? loan.getDisbursedAmount() : loan.getAmount();
        netPaidOut = netPaidOut.add(net);
        loanCharges = loanCharges.add(loan.getAmount().subtract(net).max(BigDecimal.ZERO));
        months.get(key(paidOn)).loansPaidOut = months.get(key(paidOn)).loansPaidOut.add(loan.getAmount());
        typeTotals.loansPaidOut++;
        typeTotals.principal = typeTotals.principal.add(loan.getAmount());
      }

      if (LoanSchedule.isRunning(loan)) {
        activeLoans++;
        BigDecimal owed = LoanSchedule.outstanding(loan, repaid);
        outstanding = outstanding.add(owed);
        typeTotals.outstanding = typeTotals.outstanding.add(owed);
        int late = LoanSchedule.overdueInstallments(loan, today);
        if (late > 0) {
          overdueLoans++;
          overdueOutstanding = overdueOutstanding.add(owed);
          overdue.add(
              new FinancialReportDto.OverdueLoan(
                  loan.getId().toString(),
                  loan.getMemberId(),
                  memberNames.getOrDefault(loan.getMemberId(), "Unknown member"),
                  typeName,
                  owed,
                  late,
                  LoanSchedule.oldestOverdueDate(loan, today)));
        }
      }
    }

    // Repayments in range, and the interest share of each (the loan's interest / total repayment).
    BigDecimal repaymentsCollected = BigDecimal.ZERO, interestEarned = BigDecimal.ZERO;
    int repaymentCount = 0;
    for (LoanRepayment repayment : repayments) {
      if (!inRange(repayment.getRepaymentDate(), start, end)) continue;
      repaymentsCollected = repaymentsCollected.add(repayment.getAmount());
      repaymentCount++;
      MonthTotals month = months.get(key(repayment.getRepaymentDate()));
      month.repayments = month.repayments.add(repayment.getAmount());
      LoanRecord loan = loansById.get(repayment.getLoanId());
      if (loan != null) {
        if (loan.getTotalRepayment().signum() > 0) {
          BigDecimal interest = loan.getTotalRepayment().subtract(loan.getAmount());
          interestEarned =
              interestEarned.add(
                  repayment.getAmount().multiply(interest).divide(loan.getTotalRepayment(), 2, RoundingMode.HALF_UP));
        }
        TypeTotals typeTotals = byType.get(typeNames.getOrDefault(loan.getLoanTypeId(), "Unknown type"));
        if (typeTotals != null) typeTotals.repaid = typeTotals.repaid.add(repayment.getAmount());
      }
    }

    overdue.sort(Comparator.comparing(FinancialReportDto.OverdueLoan::outstanding).reversed());

    return ResponseEntity.ok(
        new FinancialReportDto(
            coop.getId(),
            coop.getName(),
            coop.getCurrency(),
            start,
            end,
            LocalDateTime.now(),
            new FinancialReportDto.Savings(
                deposits,
                depositCount,
                withdrawals,
                withdrawalCount,
                withinDebits,
                withinCredits,
                deposits.subtract(withdrawals).subtract(withinDebits).add(withinCredits),
                closing),
            new FinancialReportDto.Loans(
                applications,
                paidOut,
                principalPaidOut,
                netPaidOut,
                rejected,
                repaymentsCollected,
                repaymentCount,
                activeLoans,
                outstanding,
                overdueLoans,
                overdueOutstanding),
            new FinancialReportDto.Income(
                interestEarned,
                loanCharges,
                savingsCharges,
                withdrawalFees,
                interestEarned.add(loanCharges).add(savingsCharges).add(withdrawalFees)),
            months.entrySet().stream()
                .map(e -> new FinancialReportDto.Month(
                    e.getKey(), e.getValue().deposits, e.getValue().withdrawals, e.getValue().loansPaidOut, e.getValue().repayments))
                .toList(),
            byType.entrySet().stream()
                .filter(e -> e.getValue().loansPaidOut > 0 || e.getValue().outstanding.signum() > 0 || e.getValue().repaid.signum() > 0)
                .map(e -> new FinancialReportDto.LoanTypeLine(
                    e.getKey(), e.getValue().loansPaidOut, e.getValue().principal, e.getValue().repaid, e.getValue().outstanding))
                .toList(),
            overdue));
  }

  private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
    return date != null && !date.isBefore(start) && !date.isAfter(end);
  }

  private static String key(LocalDate date) {
    return YearMonth.from(date).toString();
  }

  /** Every month the range touches, in order, so empty months still show as zero. */
  private static Map<String, MonthTotals> monthsBetween(LocalDate start, LocalDate end) {
    Map<String, MonthTotals> months = new LinkedHashMap<>();
    for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
      months.put(m.toString(), new MonthTotals());
    }
    return months;
  }

  private static final class MonthTotals {
    BigDecimal deposits = BigDecimal.ZERO;
    BigDecimal withdrawals = BigDecimal.ZERO;
    BigDecimal loansPaidOut = BigDecimal.ZERO;
    BigDecimal repayments = BigDecimal.ZERO;
  }

  private static final class TypeTotals {
    int loansPaidOut;
    BigDecimal principal = BigDecimal.ZERO;
    BigDecimal repaid = BigDecimal.ZERO;
    BigDecimal outstanding = BigDecimal.ZERO;
  }
}
