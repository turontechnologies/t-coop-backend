package com.turontechnologies.tcoop.loan;

/** The repayment just recorded, plus the loan's own updated status/repaymentsMade — so the
 * frontend can show "Completed" immediately without a second fetch. */
public record LoanRepaymentResultDto(LoanRepaymentDto repayment, LoanRecordDto loan) {}
