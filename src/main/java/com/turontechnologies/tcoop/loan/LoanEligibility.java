package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much a member can borrow, and what it costs — shared by the loan application endpoint and
 * {@code DashboardService}'s "Loan Eligibility" card so the two never drift.
 *
 * <p>Two loan modes:
 * <ul>
 *   <li>{@link #TIMES_TWO} — up to half the member's savings (their savings must be at least twice
 *       the loan). Needs a guarantor.
 *   <li>{@link #WITHIN} — up to the member's savings; borrowing from their own savings. No
 *       guarantor.
 * </ul>
 * Either way it's also capped by the loan type's own maximum, and interest follows the loan
 * type: a percentage of the amount, a fixed amount, or none for a "NoInterest" type.
 */
public final class LoanEligibility {

  public static final String TIMES_TWO = "TimesTwo";
  public static final String WITHIN = "Within";

  private LoanEligibility() {}

  public static boolean isValidMode(String mode) {
    return TIMES_TWO.equals(mode) || WITHIN.equals(mode);
  }

  /** The most this member can borrow on this type in this mode — never negative. */
  public static BigDecimal forMode(BigDecimal totalSavings, LoanType loanType, String mode) {
    BigDecimal savings = totalSavings.max(BigDecimal.ZERO);
    BigDecimal byMode =
        WITHIN.equals(mode) ? savings : savings.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
    return byMode.min(loanType.getMaxAmount()).setScale(2, RoundingMode.DOWN);
  }

  /** The largest amount across both modes (Within is always the larger) — for the dashboard. */
  public static BigDecimal best(BigDecimal totalSavings, LoanType loanType) {
    return forMode(totalSavings, loanType, WITHIN);
  }

  /** Total interest for the whole loan, by the loan type's own interest type. */
  public static BigDecimal totalInterest(LoanType loanType, BigDecimal amount) {
    BigDecimal rate = loanType.getInterestAmount() == null ? BigDecimal.ZERO : loanType.getInterestAmount();
    return switch (loanType.getInterestType()) {
      case "NoInterest" -> BigDecimal.ZERO.setScale(2);
      case "Fixed" -> rate.setScale(2, RoundingMode.HALF_UP);
      default -> amount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    };
  }
}
