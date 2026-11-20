package com.turontechnologies.tcoop.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/cooperatives/{id}/reports/financial?from=&to= — one co-op's financial report for a
 * date range. Flows (deposits, payouts, repayments, income) cover the range; positions
 * (outstanding, overdue, ageing, in progress) are as of today.
 */
public record FinancialReportDto(
    String cooperativeId,
    String cooperativeName,
    String currency,
    LocalDate from,
    LocalDate to,
    LocalDateTime generatedAt,
    Savings savings,
    Loans loans,
    Income income,
    List<Month> monthly,
    List<LoanTypeLine> byLoanType,
    List<OverdueLoan> overdue,
    List<AgeingBucket> ageing,
    List<MemberLine> members) {

  public record Savings(
      BigDecimal deposits,
      int depositCount,
      BigDecimal withdrawals,
      int withdrawalCount,
      /** Taken out of members' savings to fund Within loans. */
      BigDecimal withinLoanDebits,
      /** Repaid Within-loan principal returned to members' savings. */
      BigDecimal withinLoanCredits,
      BigDecimal netChange,
      /** Total of all members' savings at the end of the range. */
      BigDecimal closingBalance,
      /** Total of all members' savings the day before the range starts. */
      BigDecimal openingBalance,
      /** How many different members made a deposit in the range. */
      int savers) {}

  public record Loans(
      int applications,
      int paidOut,
      BigDecimal principalPaidOut,
      /** What actually reached members' accounts — principal minus loan charges. */
      BigDecimal netPaidOut,
      int rejected,
      BigDecimal repaymentsCollected,
      int repaymentCount,
      int activeLoans,
      BigDecimal outstandingBalance,
      int overdueLoans,
      BigDecimal overdueOutstanding,
      /** What members asked for on the applications made in the range. */
      BigDecimal appliedAmount,
      /** Loans whose last committee approval fell in the range. */
      int approved,
      BigDecimal approvedAmount,
      /** Today: requests still waiting on guarantors, the committee or the borrower. */
      int inProgress,
      /** Today: approved loans waiting to be paid out. */
      int pendingDisbursement,
      /** One loan waiting to be paid out, so "needs attention" can link straight to it. */
      String firstPendingDisbursementId,
      /** Today: outstanding on loans more than 30 days late. */
      BigDecimal par30Amount) {}

  public record Income(
      BigDecimal interestEarned,
      BigDecimal loanCharges,
      BigDecimal savingsCharges,
      BigDecimal withdrawalFees,
      BigDecimal total,
      BigDecimal applicationFees) {}

  public record Month(
      String month,
      BigDecimal deposits,
      BigDecimal withdrawals,
      BigDecimal loansPaidOut,
      BigDecimal repayments,
      BigDecimal interest,
      /** Application fees, loan charges, savings charges and withdrawal fees. */
      BigDecimal fees,
      BigDecimal income,
      /** All members' savings at the end of the month (or the range end, if sooner). */
      BigDecimal closingBalance) {}

  public record LoanTypeLine(
      String loanType,
      int loansPaidOut,
      BigDecimal principal,
      BigDecimal repaid,
      BigDecimal outstanding,
      String loanTypeId,
      int months,
      String interestType,
      BigDecimal interestAmount,
      int applications,
      BigDecimal interest,
      /** Today: what's past due on this type's loans. */
      BigDecimal overdue) {}

  public record OverdueLoan(
      String loanId,
      String memberId,
      String memberName,
      String loanType,
      BigDecimal outstanding,
      int overdueInstallments,
      LocalDate oldestDueDate,
      long daysLate,
      /** The missed installments added up (never more than what's outstanding). */
      BigDecimal amountDue) {}

  /** Outstanding on running loans, grouped by how late the oldest unpaid installment is. */
  public record AgeingBucket(String name, int count, BigDecimal amount) {}

  public record MemberLine(
      String memberId,
      String name,
      String role,
      String avatarUrl,
      BigDecimal opening,
      BigDecimal deposits,
      BigDecimal withdrawals,
      BigDecimal charges,
      BigDecimal closing,
      LocalDate lastDeposit,
      BigDecimal outstanding,
      int activeLoans,
      int overdueInstallments,
      /** Share of repayments made on time, 0–100; null when they've made none. */
      Integer onTimeRate,
      /** Share of marked meetings attended, 0–100; null when none were marked. */
      Integer attendanceRate,
      LocalDate lastActivity) {}
}
