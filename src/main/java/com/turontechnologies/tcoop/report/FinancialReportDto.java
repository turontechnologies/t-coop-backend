package com.turontechnologies.tcoop.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/cooperatives/{id}/reports/financial?from=&to= — one co-op's financial report for a
 * date range. Flows (deposits, payouts, repayments, income) cover the range; balances
 * (outstanding, overdue) are as of today.
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
    List<OverdueLoan> overdue) {

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
      BigDecimal closingBalance) {}

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
      BigDecimal overdueOutstanding) {}

  public record Income(
      BigDecimal interestEarned,
      BigDecimal loanCharges,
      BigDecimal savingsCharges,
      BigDecimal withdrawalFees,
      BigDecimal total) {}

  public record Month(
      String month, BigDecimal deposits, BigDecimal withdrawals, BigDecimal loansPaidOut, BigDecimal repayments) {}

  public record LoanTypeLine(
      String loanType, int loansPaidOut, BigDecimal principal, BigDecimal repaid, BigDecimal outstanding) {}

  public record OverdueLoan(
      String loanId,
      String memberId,
      String memberName,
      String loanType,
      BigDecimal outstanding,
      int overdueInstallments,
      LocalDate oldestDueDate) {}
}
