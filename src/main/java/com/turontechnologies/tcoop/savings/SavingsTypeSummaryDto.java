package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One row of the super-admin "Members Savings" breakdown table — GET
 * /api/v1/cooperatives/{id}/savings/types. Matches the frontend's (currently client-computed)
 * CoopSavingsTypeSummary shape in coop-data.ts, now computed server-side instead.
 */
public record SavingsTypeSummaryDto(
    String id,
    String name,
    BigDecimal min,
    BigDecimal max,
    String status,
    BigDecimal earnings,
    BigDecimal total) {

  /** Matches the frontend's SAVINGS_EARNINGS_RATE — an illustrative dividend figure (2% of a
   * type's total), not a real interest-accrual engine; nothing else in this codebase pays
   * savings interest yet. */
  private static final BigDecimal EARNINGS_RATE = new BigDecimal("0.02");

  public static SavingsTypeSummaryDto from(SavingsType type, BigDecimal total) {
    BigDecimal earnings = total.multiply(EARNINGS_RATE).setScale(2, RoundingMode.HALF_UP);
    return new SavingsTypeSummaryDto(
        type.getId().toString(),
        type.getName(),
        type.getMinAmount(),
        type.getMaxAmount(),
        type.getStatus(),
        earnings,
        total);
  }
}
