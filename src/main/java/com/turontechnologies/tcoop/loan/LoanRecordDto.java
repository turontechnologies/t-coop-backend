package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One loan record — GET /api/v1/cooperatives/{id}/loans (list) and GET /api/v1/loans/{recordId}
 * (single). Matches the frontend's CoopLoanRecord shape in coop-data.ts, including the joined-in
 * memberName/loanType display strings.
 */
public record LoanRecordDto(
    String id,
    String memberId,
    String memberName,
    String loanType,
    BigDecimal amount,
    BigDecimal interestRate,
    int durationMonths,
    int numberOfRepayments,
    BigDecimal monthlyRepayment,
    BigDecimal totalRepayment,
    String guarantorName,
    LocalDate date,
    String status,
    int repaymentsMade,
    String guarantorDocumentUrl,
    String rejectionReason) {

  public static LoanRecordDto from(LoanRecord record, String memberName, String loanTypeName) {
    return new LoanRecordDto(
        record.getId().toString(),
        record.getMemberId(),
        memberName,
        loanTypeName,
        record.getAmount(),
        record.getInterestRate(),
        record.getDurationMonths(),
        record.getNumberOfRepayments(),
        record.getMonthlyRepayment(),
        record.getTotalRepayment(),
        record.getGuarantorName(),
        record.getLoanDate(),
        record.getStatus(),
        record.getRepaymentsMade(),
        record.getGuarantorDocumentUrl(),
        record.getRejectionReason());
  }
}
