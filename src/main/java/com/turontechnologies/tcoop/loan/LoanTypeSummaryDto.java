package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

/**
 * One row of the super-admin "Loans" breakdown table — GET /api/v1/cooperatives/{id}/loans/types.
 * Matches the frontend's (currently client-computed) CoopLoanTypeSummary shape in coop-data.ts,
 * now computed server-side instead.
 */
public record LoanTypeSummaryDto(
    String id,
    String name,
    BigDecimal eligibilityPercent,
    int durationMonths,
    int numberOfRepayments,
    BigDecimal interestRate,
    String status,
    BigDecimal earnings) {

  /** "Earnings on Loan" = interest actually collected — sum(totalRepayment - amount) over every
   * non-Rejected loan of this type, matching the frontend's coopLoansBySummaryType exactly. */
  public static LoanTypeSummaryDto from(LoanType type, BigDecimal earnings) {
    return new LoanTypeSummaryDto(
        type.getId().toString(),
        type.getName(),
        type.getEligibilityPercent(),
        type.getDurationMonths(),
        type.getNumberOfInstallments(),
        type.getInterestAmount(),
        type.getStatus(),
        earnings);
  }
}
