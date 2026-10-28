package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * When a loan's installments fall due, and what's still owed — shared by the borrower history
 * and the financial report so both read a loan the same way. Installments are monthly (a loan's
 * {@code numberOfRepayments} is its duration in months), counted from the day it was paid out,
 * or from the application date for a loan that predates payout tracking.
 */
public final class LoanSchedule {

  private LoanSchedule() {}

  /** When the loan started running — payout day, else the application date. */
  public static LocalDate startDate(LoanRecord loan) {
    return loan.getDisbursedAt() != null ? loan.getDisbursedAt().toLocalDate() : loan.getLoanDate();
  }

  public static LocalDate dueDate(LoanRecord loan, int installmentNumber) {
    return startDate(loan).plusMonths(installmentNumber);
  }

  /** Whether the loan is one a member actually owes on (paid out and not finished). */
  public static boolean isRunning(LoanRecord loan) {
    return "Active".equals(loan.getStatus());
  }

  /** Installments past their due date and not yet paid, as of {@code today}. */
  public static int overdueInstallments(LoanRecord loan, LocalDate today) {
    if (!isRunning(loan)) return 0;
    int overdue = 0;
    for (int n = loan.getRepaymentsMade() + 1; n <= loan.getNumberOfRepayments(); n++) {
      if (dueDate(loan, n).isBefore(today)) overdue++;
    }
    return overdue;
  }

  /** The earliest unpaid installment that's already past due, or null. */
  public static LocalDate oldestOverdueDate(LoanRecord loan, LocalDate today) {
    if (!isRunning(loan) || loan.getRepaymentsMade() >= loan.getNumberOfRepayments()) return null;
    LocalDate next = dueDate(loan, loan.getRepaymentsMade() + 1);
    return next.isBefore(today) ? next : null;
  }

  /** What's still owed on a running loan (total repayment minus everything paid so far). */
  public static BigDecimal outstanding(LoanRecord loan, BigDecimal repaid) {
    if (!isRunning(loan)) return BigDecimal.ZERO;
    return loan.getTotalRepayment().subtract(repaid).max(BigDecimal.ZERO);
  }

  /** Days late a repayment was (0 when on time or early). */
  public static long daysLate(LoanRecord loan, int installmentNumber, LocalDate paidOn) {
    long late = ChronoUnit.DAYS.between(dueDate(loan, installmentNumber), paidOn);
    return Math.max(0, late);
  }
}
