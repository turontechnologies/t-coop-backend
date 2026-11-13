package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One loan record — GET /api/v1/cooperatives/{id}/loans (list) and GET /api/v1/loans/{recordId}
 * (single). {@code amount}/{@code durationMonths} are the live terms (what the committee settled
 * on so far); {@code requestedAmount}/{@code requestedMonths} are what the member asked for.
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
    List<GuarantorDto> guarantors,
    BigDecimal requestedAmount,
    int requestedMonths,
    /** 1-based committee position, while "Awaiting Approval". */
    Integer approvalStep,
    /** Who it's waiting on while "Awaiting Approval", e.g. "Treasurer" — for "Awaiting Treasurer". */
    String approvalStepTitle,
    LocalDateTime offerRespondedAt,
    String payoutAccountType,
    String payoutBankName,
    String payoutAccountNumber,
    String payoutAccountName,
    Boolean payoutNameVerified,
    String disbursementMethod,
    String chequeUrl,
    String chequeNumber,
    String disbursedByName,
    BigDecimal applicationFeeAmount,
    String applicationFeeReference,
    /** Repaid so far and still owed (principal + interest) — see LoanBalances. */
    BigDecimal amountRepaid,
    BigDecimal outstandingBalance,
    /** Instalments past their due date and unpaid, what they come to, and the next due date. */
    int overdueInstallments,
    BigDecimal overdueAmount,
    LocalDate nextDueDate) {

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
    return from(record, memberName, loanTypeName, List.of(), null);
  }

  public static LoanRecordDto from(
      LoanRecord record, String memberName, String loanTypeName, List<LoanGuarantor> guarantors) {
    return from(record, memberName, loanTypeName, guarantors, null);
  }

  public static LoanRecordDto from(
      LoanRecord record,
      String memberName,
      String loanTypeName,
      List<LoanGuarantor> guarantors,
      String approvalStepTitle) {
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
        guarantors.stream().map(GuarantorDto::from).toList(),
        record.getRequestedAmount(),
        record.getRequestedMonths(),
        record.getApprovalStep(),
        approvalStepTitle,
        record.getOfferRespondedAt(),
        record.getPayoutAccountType(),
        record.getPayoutBankName(),
        record.getPayoutAccountNumber(),
        record.getPayoutAccountName(),
        record.getPayoutNameVerified(),
        record.getDisbursementMethod(),
        record.getChequeUrl(),
        record.getChequeNumber(),
        record.getDisbursedByName(),
        record.getApplicationFeeAmount(),
        record.getApplicationFeeReference(),
        LoanBalances.repaid(record),
        LoanBalances.outstanding(record),
        LoanBalances.overdueInstallments(record, LocalDate.now()),
        LoanBalances.overdueAmount(record, LocalDate.now()),
        LoanBalances.nextDueDate(record));
  }
}
