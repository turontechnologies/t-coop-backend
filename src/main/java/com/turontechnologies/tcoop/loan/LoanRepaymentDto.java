package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRepaymentDto(
    String id,
    String loanId,
    int installmentNumber,
    BigDecimal amount,
    String method,
    String transactionId,
    LocalDate date,
    String status) {

  public static LoanRepaymentDto from(LoanRepayment repayment) {
    return new LoanRepaymentDto(
        repayment.getId().toString(),
        repayment.getLoanId().toString(),
        repayment.getInstallmentNumber(),
        repayment.getAmount(),
        repayment.getMethod(),
        repayment.getTransactionId(),
        repayment.getRepaymentDate(),
        repayment.getStatus());
  }
}
