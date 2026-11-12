package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a co-op's dividends are worked out, by the method its admin chose (Settings -> Loans):
 * <ul>
 *   <li>{@code SavingsPercent}: each member gets {@code rate}% of their savings.
 *   <li>{@code InterestShare}: {@code rate}% of the interest the co-op has earned on loans is shared
 *       out, each member getting the same share of it as their share of the co-op's savings.
 *   <li>{@code None}: no dividends.
 * </ul>
 */
public final class DividendCalculator {

  private DividendCalculator() {}

  /** One member's dividend. */
  public static BigDecimal forMember(
      Cooperative coop, BigDecimal memberSavings, BigDecimal coopSavings, BigDecimal interestEarned) {
    BigDecimal savings = memberSavings.max(BigDecimal.ZERO);
    BigDecimal rate = coop.getDividendRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    return switch (coop.getDividendMethod()) {
      case "None" -> BigDecimal.ZERO.setScale(2);
      case "InterestShare" -> {
        if (coopSavings.signum() <= 0) yield BigDecimal.ZERO.setScale(2);
        BigDecimal pool = interestEarned.max(BigDecimal.ZERO).multiply(rate);
        yield pool.multiply(savings).divide(coopSavings, 2, RoundingMode.HALF_UP);
      }
      default -> savings.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    };
  }

  /** The co-op's total dividend. */
  public static BigDecimal forCooperative(Cooperative coop, BigDecimal coopSavings, BigDecimal interestEarned) {
    BigDecimal rate = coop.getDividendRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    return switch (coop.getDividendMethod()) {
      case "None" -> BigDecimal.ZERO.setScale(2);
      case "InterestShare" -> interestEarned.max(BigDecimal.ZERO).multiply(rate).setScale(2, RoundingMode.HALF_UP);
      default -> coopSavings.max(BigDecimal.ZERO).multiply(rate).setScale(2, RoundingMode.HALF_UP);
    };
  }

  /** Interest actually earned so far: each loan's interest, in proportion to the instalments
   * repaid. */
  public static BigDecimal interestEarned(Iterable<LoanRecord> loans) {
    BigDecimal total = BigDecimal.ZERO;
    for (LoanRecord loan : loans) {
      if (loan.getNumberOfRepayments() <= 0 || loan.getRepaymentsMade() <= 0) continue;
      BigDecimal interest = loan.getTotalRepayment().subtract(loan.getAmount());
      int paid = Math.min(loan.getRepaymentsMade(), loan.getNumberOfRepayments());
      total = total.add(interest.multiply(BigDecimal.valueOf(paid)).divide(BigDecimal.valueOf(loan.getNumberOfRepayments()), 2, RoundingMode.HALF_UP));
    }
    return total;
  }
}
