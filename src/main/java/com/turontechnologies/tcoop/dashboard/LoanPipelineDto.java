package com.turontechnologies.tcoop.dashboard;

import java.math.BigDecimal;

/**
 * Loans not yet paid out, for the line under the dashboard's Loans card. They never count in
 * "still owed".
 */
public record LoanPipelineDto(
    BigDecimal inProgressAmount,
    int inProgressCount,
    /** Waiting on guarantors or the loan committee. */
    int awaitingApproval,
    /** Approved, waiting for the borrower to accept. */
    int awaitingBorrower,
    /** Accepted, waiting to be paid out. */
    int readyToPayOut) {

  static LoanPipelineDto of(Iterable<com.turontechnologies.tcoop.loan.LoanRecord> loans) {
    BigDecimal amount = BigDecimal.ZERO;
    int count = 0, approval = 0, borrower = 0, ready = 0;
    for (var loan : loans) {
      switch (loan.getStatus()) {
        case "Awaiting Guarantor", "Awaiting Approval" -> approval++;
        case "Awaiting Acceptance" -> borrower++;
        case "Pending Disbursement" -> ready++;
        default -> {
          continue;
        }
      }
      count++;
      amount = amount.add(loan.getAmount());
    }
    return new LoanPipelineDto(amount, count, approval, borrower, ready);
  }
}
