package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A bank-transfer repayment a borrower submitted, for the borrower's history and the admin's queue. */
public record LoanRepaymentClaimDto(
    String id,
    String loanId,
    String memberId,
    String memberName,
    String loanTypeName,
    int installments,
    BigDecimal amount,
    String receiptUrl,
    String transferReference,
    LocalDate paidOn,
    String note,
    String status,
    LocalDateTime submittedAt,
    LocalDateTime decidedAt,
    String decidedByName,
    String rejectionReason) {

  public static LoanRepaymentClaimDto from(LoanRepaymentClaim claim, String memberName, String loanTypeName) {
    return new LoanRepaymentClaimDto(
        claim.getId().toString(),
        claim.getLoanId().toString(),
        claim.getMemberId(),
        memberName,
        loanTypeName,
        claim.getInstallments(),
        claim.getAmount(),
        claim.getReceiptUrl(),
        claim.getTransferReference(),
        claim.getPaidOn(),
        claim.getNote(),
        claim.getStatus(),
        claim.getSubmittedAt(),
        claim.getDecidedAt(),
        claim.getDecidedByName(),
        claim.getRejectionReason());
  }
}
