package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The eligibility formula shared by {@code DashboardService}'s "Loan Eligibility" summary card
 * (best across all of a co-op's active loan types) and the loan-application endpoint (checked
 * against the one specific type being applied for) — kept in one place so the two never drift. */
public final class LoanEligibility {

  private static final BigDecimal MIN_ELIGIBLE_AMOUNT = new BigDecimal("10000");

  private LoanEligibility() {}

  public static BigDecimal forType(BigDecimal totalSavings, LoanType loanType) {
    BigDecimal uncapped =
        totalSavings
            .multiply(loanType.getEligibilityPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .max(MIN_ELIGIBLE_AMOUNT);
    return uncapped.min(loanType.getMaxAmount());
  }
}
