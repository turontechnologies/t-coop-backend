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

  /** The principal still owed on a running loan (its share of each instalment that's left). */
  public static BigDecimal principalOwed(LoanRecord loan) {
    if (!"Active".equals(loan.getStatus())) return BigDecimal.ZERO.setScale(2);
    int n = Math.max(1, loan.getNumberOfRepayments());
    int left = Math.max(0, n - Math.max(0, loan.getRepaymentsMade()));
    return loan.getAmount().multiply(BigDecimal.valueOf(left)).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
  }
}
