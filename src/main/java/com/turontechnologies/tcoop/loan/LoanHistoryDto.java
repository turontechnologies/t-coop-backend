package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/cooperatives/{id}/members/{memberId}/loan-history — everything an officer needs to
 * judge a loan request: the member's savings, what they still owe, how reliably they've repaid,
 * and every loan they've had with its full repayment record.
 */
public record LoanHistoryDto(
    String memberId,
    String memberName,
    BigDecimal totalSavings,
    Summary summary,
    List<Loan> loans) {

  public record Summary(
      int activeLoans,
      int completedLoans,
      int rejectedLoans,
      int pendingLoans,
      BigDecimal outstandingBalance,
      BigDecimal totalBorrowed,
      BigDecimal totalRepaid,
      int repaymentsMade,
      int onTimeRepayments,
      int lateRepayments,
      /** Share of repayments made on or before their due date, 0–100; null with no repayments yet. */
      Integer onTimeRate,
      /** Unpaid installments already past due, across every running loan. */
      int overdueInstallments,
      LocalDate lastRepaymentDate) {}

  public record Loan(
      String id,
      String loanType,
      String loanMode,
      String status,
      BigDecimal amount,
      BigDecimal totalRepayment,
      BigDecimal repaid,
      BigDecimal outstanding,
      int numberOfRepayments,
      int repaymentsMade,
      int overdueInstallments,
      LocalDate appliedOn,
      LocalDate paidOutOn,
      /** The next unpaid installment's due date, for a running loan. */
      LocalDate nextDueDate,
      List<Repayment> repayments) {}

  public record Repayment(
      int installmentNumber,
      BigDecimal amount,
      String method,
      LocalDate paidOn,
      LocalDate dueDate,
      long daysLate) {}
}
