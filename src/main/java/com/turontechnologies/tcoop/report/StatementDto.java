package com.turontechnologies.tcoop.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/cooperatives/{id}/reports/members/{memberId}/statement?from=&to= — one member's
 * savings movements (with a running balance) and loan activity for a date range.
 */
public record StatementDto(
    String memberId,
    String memberName,
    String currency,
    LocalDate from,
    LocalDate to,
    BigDecimal opening,
    BigDecimal closing,
    /** What they owe on running loans today. */
    BigDecimal owesToday,
    List<SavingsRow> savingsRows,
    List<LoanRow> loanRows) {

  /** type: Deposit, Withdrawal or Loan. amount is signed; balance is after this row. */
  public record SavingsRow(
      LocalDate date, String type, String details, BigDecimal amount, BigDecimal balance, String reference) {}

  /** amount is positive for money paid out to the member, negative for a repayment. */
  public record LoanRow(LocalDate date, String details, BigDecimal amount, String loanId, String reference) {}
}
