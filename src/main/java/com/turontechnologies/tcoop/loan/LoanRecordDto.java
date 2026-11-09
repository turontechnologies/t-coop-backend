package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One loan record — GET /api/v1/cooperatives/{id}/loans (list) and GET /api/v1/loans/{recordId}
 * (single). Matches the frontend's CoopLoanRecord shape in coop-data.ts, including the joined-in
 * memberName/loanType display strings. The disbursement fields stay null until the Secretary's
 * approval pays the loan out.
 */
public record LoanRecordDto(
    String id,
    String memberId,
    String memberName,
    String loanType,
    String loanMode,
    BigDecimal amount,
    BigDecimal interestRate,
    int durationMonths,
    int numberOfRepayments,
    BigDecimal monthlyRepayment,
    BigDecimal totalRepayment,
    String guarantorId,
    String guarantorName,
    LocalDate date,
    String status,
    int repaymentsMade,
    String guarantorDocumentUrl,
    String rejectionReason,
    BigDecimal disbursedAmount,
    String disbursementReference,
    LocalDateTime disbursedAt,
    /** Every guarantor and where each stands (a Times two loan has two or more). */
    List<GuarantorDto> guarantors) {

  public record GuarantorDto(
      String memberId, String name, String status, String documentUrl, LocalDateTime respondedAt) {
    static GuarantorDto from(LoanGuarantor guarantor) {
      return new GuarantorDto(
          guarantor.getMemberId(),
          guarantor.getMemberName(),
          guarantor.getStatus(),
          guarantor.getDocumentUrl(),
          guarantor.getRespondedAt());
    }
  }


  public static LoanRecordDto from(LoanRecord record, String memberName, String loanTypeName) {
    return from(record, memberName, loanTypeName, List.of());
  }

  public static LoanRecordDto from(
      LoanRecord record, String memberName, String loanTypeName, List<LoanGuarantor> guarantors) {
    return new LoanRecordDto(
        record.getId().toString(),
        record.getMemberId(),
        memberName,
        loanTypeName,
        record.getLoanMode(),
        record.getAmount(),
        record.getInterestRate(),
        record.getDurationMonths(),
        record.getNumberOfRepayments(),
        record.getMonthlyRepayment(),
        record.getTotalRepayment(),
        record.getGuarantorId(),
        record.getGuarantorName(),
        record.getLoanDate(),
        record.getStatus(),
        record.getRepaymentsMade(),
        record.getGuarantorDocumentUrl(),
        record.getRejectionReason(),
        record.getDisbursedAmount(),
        record.getDisbursementReference(),
        record.getDisbursedAt(),
        guarantors.stream().map(GuarantorDto::from).toList());
  }
}
