package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What's been repaid and what's still owed on a loan, from its fixed instalments: each
 * instalment is {@code monthlyRepayment}, the last one takes whatever is left of
 * {@code totalRepayment}. Used by the dashboard, loan lists and eligibility so they agree.
 */
public final class LoanBalances {

  private LoanBalances() {}

  /** Repaid so far (principal + interest). */
  public static BigDecimal repaid(LoanRecord loan) {
    if ("Completed".equals(loan.getStatus())) return loan.getTotalRepayment();
    if (loan.getAmountRepaid() != null) return loan.getAmountRepaid().min(loan.getTotalRepayment());
    int n = loan.getNumberOfRepayments();
    int made = Math.max(0, loan.getRepaymentsMade());
    if (n <= 0 || made == 0) return BigDecimal.ZERO.setScale(2);
    if (made >= n || "Completed".equals(loan.getStatus())) return loan.getTotalRepayment();
    return loan.getMonthlyRepayment().multiply(BigDecimal.valueOf(made)).min(loan.getTotalRepayment());
  }

  /** Still owed (principal + interest) on a running loan; zero for any other status. */
  public static BigDecimal outstanding(LoanRecord loan) {
    if (!"Active".equals(loan.getStatus())) return BigDecimal.ZERO.setScale(2);
    return loan.getTotalRepayment().subtract(repaid(loan)).max(BigDecimal.ZERO);
  }

  /**
   * How many instalments should have been paid by {@code today}: instalment i falls due i months
   * after the loan was paid out. Zero for a loan that isn't running.
   */
  public static int installmentsDue(LoanRecord loan, java.time.LocalDate today) {
    java.time.LocalDate start = startOf(loan);
    if (!"Active".equals(loan.getStatus()) || start == null) return 0;
    int due = 0;
    for (int i = 1; i <= loan.getNumberOfRepayments(); i++) {
      if (start.plusMonths(i).isAfter(today)) break;
      due = i;
    }
    return due;
  }

  /** Instalments past their due date and not yet paid. */
  public static int overdueInstallments(LoanRecord loan, java.time.LocalDate today) {
    return Math.max(0, installmentsDue(loan, today) - Math.max(0, loan.getRepaymentsMade()));
  }

  /** What's overdue: what should have been paid by now minus what has been (part payments count). */
  public static BigDecimal overdueAmount(LoanRecord loan, java.time.LocalDate today) {
    if (overdueInstallments(loan, today) == 0) return BigDecimal.ZERO.setScale(2);
    BigDecimal shouldHave = cumulativeAt(loan, installmentsDue(loan, today));
    return shouldHave.subtract(repaid(loan)).max(BigDecimal.ZERO).min(outstanding(loan));
  }

  /** What has to have been paid in total once installment {@code k} is cleared. */
  public static BigDecimal cumulativeAt(LoanRecord loan, int k) {
    if (k <= 0) return BigDecimal.ZERO.setScale(2);
    if (k >= loan.getNumberOfRepayments()) return loan.getTotalRepayment();
    return loan.getMonthlyRepayment().multiply(BigDecimal.valueOf(k)).min(loan.getTotalRepayment());
  }

  /** How many installments {@code paid} fully covers. The last is only covered by the full total. */
  public static int installmentsCovered(LoanRecord loan, BigDecimal paid) {
    int n = loan.getNumberOfRepayments();
    if (n <= 0) return 0;
    if (paid.compareTo(loan.getTotalRepayment()) >= 0) return n;
    if (loan.getMonthlyRepayment().signum() <= 0) return 0;
    int covered = paid.divide(loan.getMonthlyRepayment(), 0, RoundingMode.DOWN).intValue();
    return Math.max(0, Math.min(covered, n - 1));
  }

  /** The Within-loan principal inside {@code paid}: the loan's principal share of every naira repaid. */
  public static BigDecimal principalIn(LoanRecord loan, BigDecimal paid) {
    if (loan.getTotalRepayment().signum() <= 0) return BigDecimal.ZERO.setScale(2);
    return paid.multiply(loan.getAmount()).divide(loan.getTotalRepayment(), 2, RoundingMode.HALF_UP);
  }

  /** When the next unpaid instalment falls due; null when nothing is left to pay. */
  public static java.time.LocalDate nextDueDate(LoanRecord loan) {
    java.time.LocalDate start = startOf(loan);
    if (!"Active".equals(loan.getStatus()) || start == null) return null;
    int next = Math.max(0, loan.getRepaymentsMade()) + 1;
    if (next > loan.getNumberOfRepayments()) return null;
    return start.plusMonths(next);
  }

  /** Repayments run from the payout date (older loans without one: the loan date). */
  private static java.time.LocalDate startOf(LoanRecord loan) {
    return loan.getDisbursedAt() != null ? loan.getDisbursedAt().toLocalDate() : loan.getLoanDate();
  }

  /** The principal still owed on a running loan (its share of each instalment that's left). */
  public static BigDecimal principalOwed(LoanRecord loan) {
    if (!"Active".equals(loan.getStatus())) return BigDecimal.ZERO.setScale(2);
    return loan.getAmount().subtract(principalIn(loan, repaid(loan))).max(BigDecimal.ZERO);
  }
}
