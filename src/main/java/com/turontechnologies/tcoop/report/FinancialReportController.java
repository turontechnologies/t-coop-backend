package com.turontechnologies.tcoop.report;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.coopstaff.CoopRole;
import com.turontechnologies.tcoop.coopstaff.CoopRoleRepository;
import com.turontechnologies.tcoop.loan.LoanApplicationFee;
import com.turontechnologies.tcoop.loan.LoanApplicationFeeRepository;
import com.turontechnologies.tcoop.loan.LoanApproval;
import com.turontechnologies.tcoop.loan.LoanApprovalRepository;
import com.turontechnologies.tcoop.loan.LoanRecord;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.loan.LoanRepayment;
import com.turontechnologies.tcoop.loan.LoanRepaymentRepository;
import com.turontechnologies.tcoop.loan.LoanSchedule;
import com.turontechnologies.tcoop.loan.LoanType;
import com.turontechnologies.tcoop.loan.LoanTypeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notice.MeetingAttendance;
import com.turontechnologies.tcoop.notice.MeetingAttendanceRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * income earned, what's outstanding and overdue, and each member's position — for the co-op
 * admin and any staff whose role grants Reports. Built from the same ledgers every other screen
 * reads (savings_records, loan_records, loan_repayments, savings_requests), so it always
 * reconciles with them.
 */
@RestController
public class FinancialReportController {

  private static final Set<String> ONLINE_METHODS = Set.of("Paystack", "Flutterwave");
  private static final String LOAN_OUT = "Loan Disbursement";
  private static final String LOAN_BACK = "Loan Repayment";
  /** Statuses a loan only reaches after the committee has approved it. */
  private static final Set<String> PAST_APPROVAL =
      Set.of("Awaiting Acceptance", "Pending Disbursement", "Active", "Completed", "Declined");

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final SavingsPaymentIntentRepository savingsIntentRepository;
  private final SavingsRequestRepository savingsRequestRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanRepaymentRepository loanRepaymentRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanApprovalRepository loanApprovalRepository;
  private final LoanApplicationFeeRepository applicationFeeRepository;
  private final MeetingAttendanceRepository attendanceRepository;
  private final CoopRoleRepository coopRoleRepository;
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
      LoanApprovalRepository loanApprovalRepository,
      LoanApplicationFeeRepository applicationFeeRepository,
      MeetingAttendanceRepository attendanceRepository,
      CoopRoleRepository coopRoleRepository,
      CoopPermissions coopPermissions) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.savingsIntentRepository = savingsIntentRepository;
    this.savingsRequestRepository = savingsRequestRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanRepaymentRepository = loanRepaymentRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanApprovalRepository = loanApprovalRepository;
    this.applicationFeeRepository = applicationFeeRepository;
    this.attendanceRepository = attendanceRepository;
    this.coopRoleRepository = coopRoleRepository;
    this.coopPermissions = coopPermissions;
  }

  /** The caller, the co-op and the date range — or the error response to send instead. */
  private record Scope(Cooperative coop, LocalDate start, LocalDate end, ResponseEntity<?> error) {}

  private Scope scope(Authentication authentication, String id, String from, String to) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return new Scope(null, null, null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if (!coopPermissions.allows(caller, id, "Reports", null, false)) {
      return new Scope(
          null, null, null,
          ResponseEntity.status(403).body(Map.of("error", "You don't have access to this co-operative's reports")));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return new Scope(null, null, null, ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative")));
    }
    LocalDate today = LocalDate.now();
    LocalDate start;
    LocalDate end;
    try {
      start = from == null || from.isBlank() ? today.withDayOfYear(1) : LocalDate.parse(from);
      end = to == null || to.isBlank() ? today : LocalDate.parse(to);
    } catch (DateTimeParseException e) {
      return new Scope(null, null, null, ResponseEntity.badRequest().body(Map.of("error", "Dates must look like 2026-01-31")));
    }
    if (end.isBefore(start)) {
      return new Scope(
          null, null, null, ResponseEntity.badRequest().body(Map.of("error", "The start date must be before the end date")));
    }
    return new Scope(coop, start, end, null);
  }

  @GetMapping("/api/v1/cooperatives/{id}/reports/financial")
  public ResponseEntity<?> financial(
      Authentication authentication,
      @PathVariable String id,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    Scope scope = scope(authentication, id, from, to);
    if (scope.error() != null) return scope.error();
    Cooperative coop = scope.coop();
    LocalDate start = scope.start();
    LocalDate end = scope.end();
    LocalDate today = LocalDate.now();

    List<Member> members = memberRepository.findAllByCooperativeId(id);
    Map<String, String> memberNames =
        members.stream().collect(Collectors.toMap(Member::getId, Member::getFullName, (a, b) -> a));
    List<LoanType> loanTypes = loanTypeRepository.findAllByCooperativeId(id);
    Map<UUID, String> typeNames =
        loanTypes.stream().collect(Collectors.toMap(LoanType::getId, LoanType::getName, (a, b) -> a));

    Map<String, MonthTotals> months = monthsBetween(start, end);
    Map<String, MemberTotals> perMember = new LinkedHashMap<>();
    for (Member member : members) perMember.put(member.getId(), new MemberTotals());

    // --- Savings ---
    List<SavingsRecord> savingsRecords =
        savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(r -> "Success".equals(r.getStatus()) && r.getRecordDate() != null)
            .toList();
    BigDecimal deposits = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO;
    BigDecimal withinDebits = BigDecimal.ZERO, withinCredits = BigDecimal.ZERO;
    BigDecimal closing = BigDecimal.ZERO, opening = BigDecimal.ZERO;
    int depositCount = 0, withdrawalCount = 0;
    Set<String> savers = new HashSet<>();
    List<SavingsRecord> onlineDeposits = new ArrayList<>();
    for (SavingsRecord record : savingsRecords) {
      LocalDate date = record.getRecordDate();
      BigDecimal amount = record.getAmount();
      MemberTotals mine = perMember.computeIfAbsent(record.getMemberId(), k -> new MemberTotals());
      if (date.isAfter(end)) continue;
      closing = closing.add(amount);
      mine.closing = mine.closing.add(amount);
      for (Map.Entry<String, MonthTotals> month : months.entrySet()) {
        if (!date.isAfter(monthEnd(month.getKey(), end))) {
          month.getValue().closingBalance = month.getValue().closingBalance.add(amount);
        }
      }
      String method = record.getMethod();
      boolean deposit = amount.signum() > 0 && !LOAN_BACK.equals(method);
      if (deposit && (mine.lastDeposit == null || date.isAfter(mine.lastDeposit))) mine.lastDeposit = date;
      if (date.isBefore(start)) {
        opening = opening.add(amount);
        mine.opening = mine.opening.add(amount);
        continue;
      }
      MonthTotals month = months.get(key(date));
      if (LOAN_OUT.equals(method)) {
        withinDebits = withinDebits.add(amount.abs());
      } else if (LOAN_BACK.equals(method)) {
        withinCredits = withinCredits.add(amount);
      } else if (deposit) {
        deposits = deposits.add(amount);
        depositCount++;
        savers.add(record.getMemberId());
        mine.deposits = mine.deposits.add(amount);
        month.deposits = month.deposits.add(amount);
        if (ONLINE_METHODS.contains(method)) onlineDeposits.add(record);
      } else if (amount.signum() < 0) {
        withdrawals = withdrawals.add(amount.abs());
        withdrawalCount++;
        mine.withdrawals = mine.withdrawals.add(amount.abs());
        month.withdrawals = month.withdrawals.add(amount.abs());
      }
    }

    // The savings charge on an online deposit is what the member paid minus what was credited.
    BigDecimal savingsCharges = BigDecimal.ZERO;
    Map<String, SavingsPaymentIntent> intents =
        savingsIntentRepository.findAllById(onlineDeposits.stream().map(SavingsRecord::getTransactionId).toList()).stream()
            .collect(Collectors.toMap(SavingsPaymentIntent::getReference, Function.identity(), (a, b) -> a));
    for (SavingsRecord record : onlineDeposits) {
      SavingsPaymentIntent intent = intents.get(record.getTransactionId());
      if (intent == null) continue;
      BigDecimal charge = intent.getAmount().subtract(record.getAmount()).max(BigDecimal.ZERO);
      savingsCharges = savingsCharges.add(charge);
      months.get(key(record.getRecordDate())).fees = months.get(key(record.getRecordDate())).fees.add(charge);
      MemberTotals mine = perMember.get(record.getMemberId());
      if (mine != null) mine.charges = mine.charges.add(charge);
    }

    BigDecimal withdrawalFees = BigDecimal.ZERO;
    for (SavingsRequest request : savingsRequestRepository.findAllByCooperativeIdOrderByRequestedAtDesc(id)) {
      if (!"Approved".equals(request.getStatus()) || request.getResolvedAt() == null || request.getFeeAmount() == null) continue;
      LocalDate on = request.getResolvedAt().toLocalDate();
      if (!inRange(on, start, end)) continue;
      withdrawalFees = withdrawalFees.add(request.getFeeAmount());
      months.get(key(on)).fees = months.get(key(on)).fees.add(request.getFeeAmount());
      MemberTotals mine = perMember.get(request.getMemberId());
      if (mine != null) mine.charges = mine.charges.add(request.getFeeAmount());
    }

    BigDecimal applicationFees = BigDecimal.ZERO;
    for (LoanApplicationFee fee :
        applicationFeeRepository.findAllByCooperativeIdAndStatusIn(id, List.of(LoanApplicationFee.PAID, LoanApplicationFee.USED))) {
      if (fee.getPaidAt() == null || fee.getAmount() == null) continue;
      LocalDate on = fee.getPaidAt().toLocalDate();
      if (!inRange(on, start, end)) continue;
      applicationFees = applicationFees.add(fee.getAmount());
      months.get(key(on)).fees = months.get(key(on)).fees.add(fee.getAmount());
    }

    // --- Loans ---
    List<LoanRecord> loans = loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id);
    Map<UUID, LoanRecord> loansById = loans.stream().collect(Collectors.toMap(LoanRecord::getId, Function.identity()));
    List<LoanRepayment> repayments = loanRepaymentRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id);
    Map<UUID, BigDecimal> repaidByLoan = new HashMap<>();
    for (LoanRepayment repayment : repayments) {
      repaidByLoan.merge(repayment.getLoanId(), repayment.getAmount(), BigDecimal::add);
    }
    Map<UUID, LocalDateTime> lastApproval = new HashMap<>();
    if (!loans.isEmpty()) {
      for (LoanApproval approval : loanApprovalRepository.findAllByLoanIdIn(loansById.keySet())) {
        if (!"Approved".equals(approval.getDecision()) || approval.getActedAt() == null) continue;
        lastApproval.merge(approval.getLoanId(), approval.getActedAt(), (a, b) -> a.isAfter(b) ? a : b);
      }
    }

    int applications = 0, paidOut = 0, rejected = 0, activeLoans = 0, overdueLoans = 0;
    int approved = 0, inProgress = 0, pendingDisbursement = 0;
    String firstPendingDisbursement = null;
    BigDecimal principalPaidOut = BigDecimal.ZERO, netPaidOut = BigDecimal.ZERO, loanCharges = BigDecimal.ZERO;
    BigDecimal outstanding = BigDecimal.ZERO, overdueOutstanding = BigDecimal.ZERO;
    BigDecimal appliedAmount = BigDecimal.ZERO, approvedAmount = BigDecimal.ZERO;
    Map<String, TypeTotals> byType = new LinkedHashMap<>();
    for (LoanType type : loanTypes) byType.put(type.getName(), new TypeTotals(type));
    List<FinancialReportDto.OverdueLoan> overdue = new ArrayList<>();
    String[] bucketNames = {"Current", "1–30 days", "31–60 days", "61–90 days", "Over 90 days"};
    int[] bucketCounts = new int[5];
    BigDecimal[] bucketAmounts = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};

    for (LoanRecord loan : loans) {
      String typeName = typeNames.getOrDefault(loan.getLoanTypeId(), "Unknown type");
      TypeTotals typeTotals = byType.computeIfAbsent(typeName, k -> new TypeTotals(null));
      BigDecimal repaid = repaidByLoan.getOrDefault(loan.getId(), BigDecimal.ZERO);
      String status = loan.getStatus() == null ? "" : loan.getStatus();

      if (inRange(loan.getLoanDate(), start, end)) {
        applications++;
        typeTotals.applications++;
        appliedAmount = appliedAmount.add(loan.getRequestedAmount());
        if ("Rejected".equals(status)) rejected++;
      }
      LocalDateTime approvedAt = lastApproval.get(loan.getId());
      if (PAST_APPROVAL.contains(status) && approvedAt != null && inRange(approvedAt.toLocalDate(), start, end)) {
        approved++;
        approvedAmount = approvedAmount.add(loan.getAmount());
      }
      if (status.startsWith("Awaiting") || "Pending Disbursement".equals(status)) inProgress++;
      if ("Pending Disbursement".equals(status)) {
        pendingDisbursement++;
        if (firstPendingDisbursement == null) firstPendingDisbursement = loan.getId().toString();
      }

      LocalDate paidOn = loan.getDisbursedAt() != null ? loan.getDisbursedAt().toLocalDate() : null;
      if (paidOn != null && inRange(paidOn, start, end)) {
        paidOut++;
        principalPaidOut = principalPaidOut.add(loan.getAmount());
        BigDecimal net = loan.getDisbursedAmount() != null ? loan.getDisbursedAmount() : loan.getAmount();
        netPaidOut = netPaidOut.add(net);
        BigDecimal charge = loan.getAmount().subtract(net).max(BigDecimal.ZERO);
        loanCharges = loanCharges.add(charge);
        MonthTotals month = months.get(key(paidOn));
        month.loansPaidOut = month.loansPaidOut.add(loan.getAmount());
        month.fees = month.fees.add(charge);
        typeTotals.loansPaidOut++;
        typeTotals.principal = typeTotals.principal.add(loan.getAmount());
      }

      if (LoanSchedule.isRunning(loan)) {
        activeLoans++;
        BigDecimal owed = LoanSchedule.outstanding(loan, repaid);
        outstanding = outstanding.add(owed);
        typeTotals.outstanding = typeTotals.outstanding.add(owed);
        MemberTotals mine = perMember.computeIfAbsent(loan.getMemberId(), k -> new MemberTotals());
        mine.outstanding = mine.outstanding.add(owed);
        mine.activeLoans++;
        int late = LoanSchedule.overdueInstallments(loan, today);
        long daysLate = 0;
        if (late > 0) {
          LocalDate oldest = LoanSchedule.oldestOverdueDate(loan, today);
          daysLate = oldest == null ? 0 : ChronoUnit.DAYS.between(oldest, today);
          BigDecimal due = loan.getMonthlyRepayment().multiply(BigDecimal.valueOf(late)).min(owed);
          overdueLoans++;
          overdueOutstanding = overdueOutstanding.add(owed);
          typeTotals.overdue = typeTotals.overdue.add(due);
          mine.overdueInstallments += late;
          overdue.add(
              new FinancialReportDto.OverdueLoan(
                  loan.getId().toString(),
                  loan.getMemberId(),
                  memberNames.getOrDefault(loan.getMemberId(), "Unknown member"),
                  typeName,
                  owed,
                  late,
                  oldest,
                  daysLate,
                  due));
        }
        int bucket = daysLate <= 0 ? 0 : daysLate <= 30 ? 1 : daysLate <= 60 ? 2 : daysLate <= 90 ? 3 : 4;
        bucketCounts[bucket]++;
        bucketAmounts[bucket] = bucketAmounts[bucket].add(owed);
      }
    }

    // Repayments in range, and the interest share of each (the loan's interest / total repayment).
    // On-time history and last repayment use every repayment, not just the range.
    BigDecimal repaymentsCollected = BigDecimal.ZERO, interestEarned = BigDecimal.ZERO;
    int repaymentCount = 0;
    for (LoanRepayment repayment : repayments) {
      LoanRecord loan = loansById.get(repayment.getLoanId());
      MemberTotals mine = loan == null ? null : perMember.computeIfAbsent(loan.getMemberId(), k -> new MemberTotals());
      if (loan != null && repayment.getRepaymentDate() != null && !repayment.getRepaymentDate().isAfter(end)) {
        mine.repayments++;
        if (LoanSchedule.daysLate(loan, repayment.getInstallmentNumber(), repayment.getRepaymentDate()) == 0) mine.onTime++;
        if (mine.lastRepayment == null || repayment.getRepaymentDate().isAfter(mine.lastRepayment)) {
          mine.lastRepayment = repayment.getRepaymentDate();
        }
      }
      if (!inRange(repayment.getRepaymentDate(), start, end)) continue;
      repaymentsCollected = repaymentsCollected.add(repayment.getAmount());
      repaymentCount++;
      MonthTotals month = months.get(key(repayment.getRepaymentDate()));
      month.repayments = month.repayments.add(repayment.getAmount());
      if (loan == null) continue;
      BigDecimal interest = interestShare(loan, repayment.getAmount());
      interestEarned = interestEarned.add(interest);
      month.interest = month.interest.add(interest);
      TypeTotals typeTotals = byType.get(typeNames.getOrDefault(loan.getLoanTypeId(), "Unknown type"));
      if (typeTotals != null) {
        typeTotals.repaid = typeTotals.repaid.add(repayment.getAmount());
        typeTotals.interest = typeTotals.interest.add(interest);
      }
    }

    overdue.sort(Comparator.comparingLong(FinancialReportDto.OverdueLoan::daysLate).reversed());

    // Attendance: share of marked meetings each member attended (in person or online).
    Map<String, int[]> attendance = new HashMap<>();
    for (MeetingAttendance row : attendanceRepository.findAllByCooperativeId(id)) {
      String mark = row.getAttendance();
      if (mark == null) continue;
      int[] tally = attendance.computeIfAbsent(row.getMemberId(), k -> new int[2]);
      tally[1]++;
      if (MeetingAttendance.PRESENT.equals(mark) || MeetingAttendance.PRESENT_ONLINE.equals(mark)) tally[0]++;
    }

    Map<UUID, String> roleNames =
        coopRoleRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id).stream()
            .collect(Collectors.toMap(CoopRole::getId, CoopRole::getName, (a, b) -> a));
    List<FinancialReportDto.MemberLine> memberLines = new ArrayList<>();
    for (Member member : members) {
      MemberTotals t = perMember.getOrDefault(member.getId(), new MemberTotals());
      int[] tally = attendance.get(member.getId());
      LocalDate lastActivity =
          t.lastDeposit == null ? t.lastRepayment
              : t.lastRepayment == null || t.lastDeposit.isAfter(t.lastRepayment) ? t.lastDeposit : t.lastRepayment;
      memberLines.add(
          new FinancialReportDto.MemberLine(
              member.getId(),
              member.getFullName(),
              "admin".equals(member.getRole())
                  ? "Administrator"
                  : member.getCoopRoleId() != null ? roleNames.getOrDefault(member.getCoopRoleId(), "Staff") : "Member",
              member.getAvatarUrl(),
              t.opening,
              t.deposits,
              t.withdrawals,
              t.charges,
              t.closing,
              t.lastDeposit,
              t.outstanding,
              t.activeLoans,
              t.overdueInstallments,
              t.repayments == 0 ? null : Math.round(t.onTime * 100f / t.repayments),
              tally == null || tally[1] == 0 ? null : Math.round(tally[0] * 100f / tally[1]),
              lastActivity));
    }
    memberLines.sort(Comparator.comparing(FinancialReportDto.MemberLine::closing).reversed());

    List<FinancialReportDto.AgeingBucket> ageing = new ArrayList<>();
    for (int i = 0; i < 5; i++) ageing.add(new FinancialReportDto.AgeingBucket(bucketNames[i], bucketCounts[i], bucketAmounts[i]));
    BigDecimal par30 = bucketAmounts[2].add(bucketAmounts[3]).add(bucketAmounts[4]);

    BigDecimal incomeTotal =
        interestEarned.add(loanCharges).add(savingsCharges).add(withdrawalFees).add(applicationFees);

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
                closing,
                opening,
                savers.size()),
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
                overdueOutstanding,
                appliedAmount,
                approved,
                approvedAmount,
                inProgress,
                pendingDisbursement,
                firstPendingDisbursement,
                par30),
            new FinancialReportDto.Income(
                interestEarned, loanCharges, savingsCharges, withdrawalFees, incomeTotal, applicationFees),
            months.entrySet().stream()
                .map(e -> {
                  MonthTotals m = e.getValue();
                  return new FinancialReportDto.Month(
                      e.getKey(), m.deposits, m.withdrawals, m.loansPaidOut, m.repayments,
                      m.interest, m.fees, m.interest.add(m.fees), m.closingBalance);
                })
                .toList(),
            byType.entrySet().stream()
                .filter(e -> e.getValue().type != null
                    || e.getValue().loansPaidOut > 0
                    || e.getValue().applications > 0
                    || e.getValue().outstanding.signum() > 0
                    || e.getValue().repaid.signum() > 0)
                .map(e -> {
                  TypeTotals t = e.getValue();
                  return new FinancialReportDto.LoanTypeLine(
                      e.getKey(), t.loansPaidOut, t.principal, t.repaid, t.outstanding,
                      t.type == null ? null : t.type.getId().toString(),
                      t.type == null ? 0 : t.type.getDurationMonths(),
                      t.type == null ? null : t.type.getInterestType(),
                      t.type == null ? BigDecimal.ZERO : t.type.getInterestAmount(),
                      t.applications, t.interest, t.overdue);
                })
                .toList(),
            overdue,
            ageing,
            memberLines));
  }

  /**
   * One member's statement for the range: every savings movement with a running balance, and
   * their loans paid out and repaid.
   */
  @GetMapping("/api/v1/cooperatives/{id}/reports/members/{memberId}/statement")
  public ResponseEntity<?> statement(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String memberId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    Scope scope = scope(authentication, id, from, to);
    if (scope.error() != null) return scope.error();
    LocalDate start = scope.start();
    LocalDate end = scope.end();
    Member member = memberRepository.findById(memberId).orElse(null);
    if (member == null || !id.equals(member.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that member"));
    }

    List<SavingsRecord> records =
        savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(r -> memberId.equals(r.getMemberId()) && "Success".equals(r.getStatus()) && r.getRecordDate() != null)
            .sorted(Comparator.comparing(SavingsRecord::getRecordDate)
                .thenComparing(SavingsRecord::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
    BigDecimal opening = BigDecimal.ZERO;
    for (SavingsRecord record : records) {
      if (record.getRecordDate().isBefore(start)) opening = opening.add(record.getAmount());
    }
    BigDecimal balance = opening;
    List<StatementDto.SavingsRow> savingsRows = new ArrayList<>();
    for (SavingsRecord record : records) {
      if (!inRange(record.getRecordDate(), start, end)) continue;
      balance = balance.add(record.getAmount());
      savingsRows.add(
          new StatementDto.SavingsRow(
              record.getRecordDate(),
              rowType(record),
              rowDetails(record),
              record.getAmount(),
              balance,
              record.getTransactionId()));
    }

    Map<UUID, String> typeNames =
        loanTypeRepository.findAllByCooperativeId(id).stream()
            .collect(Collectors.toMap(LoanType::getId, LoanType::getName, (a, b) -> a));
    List<LoanRecord> loans =
        loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(l -> memberId.equals(l.getMemberId()))
            .toList();
    Map<UUID, LoanRecord> loansById = loans.stream().collect(Collectors.toMap(LoanRecord::getId, Function.identity()));
    Map<UUID, BigDecimal> repaidByLoan = new HashMap<>();
    List<StatementDto.LoanRow> loanRows = new ArrayList<>();
    for (LoanRepayment repayment : loanRepaymentRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id)) {
      LoanRecord loan = loansById.get(repayment.getLoanId());
      if (loan == null) continue;
      repaidByLoan.merge(loan.getId(), repayment.getAmount(), BigDecimal::add);
      if (!inRange(repayment.getRepaymentDate(), start, end)) continue;
      loanRows.add(
          new StatementDto.LoanRow(
              repayment.getRepaymentDate(),
              typeNames.getOrDefault(loan.getLoanTypeId(), "Loan") + " repayment",
              repayment.getAmount().negate(),
              loan.getId().toString(),
              repayment.getTransactionId()));
    }
    BigDecimal owes = BigDecimal.ZERO;
    for (LoanRecord loan : loans) {
      owes = owes.add(LoanSchedule.outstanding(loan, repaidByLoan.getOrDefault(loan.getId(), BigDecimal.ZERO)));
      LocalDate paidOn = loan.getDisbursedAt() != null ? loan.getDisbursedAt().toLocalDate() : null;
      if (paidOn != null && inRange(paidOn, start, end)) {
        loanRows.add(
            new StatementDto.LoanRow(
                paidOn,
                typeNames.getOrDefault(loan.getLoanTypeId(), "Loan") + " paid out",
                loan.getAmount(),
                loan.getId().toString(),
                loan.getDisbursementReference()));
      }
    }
    loanRows.sort(Comparator.comparing(StatementDto.LoanRow::date));

    return ResponseEntity.ok(
        new StatementDto(
            member.getId(),
            member.getFullName(),
            scope.coop().getCurrency(),
            start,
            end,
            opening,
            balance,
            owes,
            savingsRows,
            loanRows));
  }

  private static String rowType(SavingsRecord record) {
    if (LOAN_OUT.equals(record.getMethod())) return "Loan";
    if (LOAN_BACK.equals(record.getMethod())) return "Loan";
    return record.getAmount().signum() >= 0 ? "Deposit" : "Withdrawal";
  }

  private static String rowDetails(SavingsRecord record) {
    String method = Objects.toString(record.getMethod(), "");
    if (LOAN_OUT.equals(method)) return "Lent out of savings (Within loan)";
    if (LOAN_BACK.equals(method)) return "Within loan repaid into savings";
    if (record.getAmount().signum() < 0) {
      return "Bank Transfer".equals(method) ? "Withdrawal by bank transfer"
          : ONLINE_METHODS.contains(method) ? "Withdrawal paid online" : "Withdrawal";
    }
    if (ONLINE_METHODS.contains(method)) return "Paid online";
    if ("Manual Upload".equals(method)) return "Added by an admin";
    if ("Bulk Upload".equals(method)) return "Uploaded by an admin";
    return method.isBlank() ? "Deposit" : method;
  }

  /** The interest part of a repayment, in proportion to the loan's interest over its total. */
  private static BigDecimal interestShare(LoanRecord loan, BigDecimal amount) {
    if (loan.getTotalRepayment() == null || loan.getTotalRepayment().signum() <= 0) return BigDecimal.ZERO;
    BigDecimal interest = loan.getTotalRepayment().subtract(loan.getAmount()).max(BigDecimal.ZERO);
    return amount.multiply(interest).divide(loan.getTotalRepayment(), 2, RoundingMode.HALF_UP);
  }

  private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
    return date != null && !date.isBefore(start) && !date.isAfter(end);
  }

  private static String key(LocalDate date) {
    return YearMonth.from(date).toString();
  }

  /** The last day a month counts for: its own last day, or the range end if that comes first. */
  private static LocalDate monthEnd(String monthKey, LocalDate end) {
    LocalDate last = YearMonth.parse(monthKey).atEndOfMonth();
    return last.isAfter(end) ? end : last;
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
    BigDecimal interest = BigDecimal.ZERO;
    BigDecimal fees = BigDecimal.ZERO;
    BigDecimal closingBalance = BigDecimal.ZERO;
  }

  private static final class TypeTotals {
    final LoanType type;
    int loansPaidOut;
    int applications;
    BigDecimal principal = BigDecimal.ZERO;
    BigDecimal repaid = BigDecimal.ZERO;
    BigDecimal outstanding = BigDecimal.ZERO;
    BigDecimal interest = BigDecimal.ZERO;
    BigDecimal overdue = BigDecimal.ZERO;

    TypeTotals(LoanType type) {
      this.type = type;
    }
  }

  private static final class MemberTotals {
    BigDecimal opening = BigDecimal.ZERO;
    BigDecimal deposits = BigDecimal.ZERO;
    BigDecimal withdrawals = BigDecimal.ZERO;
    BigDecimal charges = BigDecimal.ZERO;
    BigDecimal closing = BigDecimal.ZERO;
    BigDecimal outstanding = BigDecimal.ZERO;
    LocalDate lastDeposit;
    LocalDate lastRepayment;
    int activeLoans;
    int overdueInstallments;
    int repayments;
    int onTime;
  }
}
