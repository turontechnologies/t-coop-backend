package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One loan a member took out — currently populated only by seed data (V3), same honest
 * limitation as SavingsRecord: the real disbursement/guarantor-approval flow still lives in the
 * frontend's mock store, not this backend. Super-admin oversight (LoanController) is read-only
 * against whatever exists here. */
@Entity
@Table(name = "loan_records")
public class LoanRecord {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "loan_type_id")
  private UUID loanTypeId;

  private BigDecimal amount;

  @Column(name = "interest_rate")
  private BigDecimal interestRate;

  @Column(name = "duration_months")
  private int durationMonths;

  @Column(name = "number_of_repayments")
  private int numberOfRepayments;

  @Column(name = "monthly_repayment")
  private BigDecimal monthlyRepayment;

  @Column(name = "total_repayment")
  private BigDecimal totalRepayment;

  @Column(name = "guarantor_id")
  private String guarantorId;

  @Column(name = "guarantor_name")
  private String guarantorName;

  @Column(name = "loan_date")
  private LocalDate loanDate;

  private String status;

  @Column(name = "repayments_made")
  private int repaymentsMade;

  @Column(name = "guarantor_document_url")
  private String guarantorDocumentUrl;

  @Column(name = "guarantor_accepted_at")
  private LocalDateTime guarantorAcceptedAt;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  /** "TimesTwo" (up to twice the member's savings, two or more guarantors) or "Within" (up to their
   * savings, no guarantor; the payout comes out of their savings and repaid principal goes back
   * in) — see LoanEligibility. */
  @Column(name = "loan_mode")
  private String loanMode;

  /** The Paystack transfer that paid this loan out, set when the Secretary's approval disburses
   * it (see LoanSelfServiceController.decide). */
  @Column(name = "disbursement_reference")
  private String disbursementReference;

  @Column(name = "disbursement_status")
  private String disbursementStatus;

  /** Principal minus loan charges — what actually reached the member's account. */
  @Column(name = "disbursed_amount")
  private BigDecimal disbursedAmount;

  @Column(name = "disbursed_at")
  private LocalDateTime disbursedAt;

  // V48: what was asked for, where the committee is, the borrower's answer and how it was paid.
  @Column(name = "requested_amount")
  private BigDecimal requestedAmount;

  @Column(name = "requested_months")
  private Integer requestedMonths;

  /** 1-based committee position the loan is waiting on, while "Awaiting Approval". */
  @Column(name = "approval_step")
  private Integer approvalStep;

  @Column(name = "offer_responded_at")
  private LocalDateTime offerRespondedAt;

  /** "Registered" (the member's own account on file) or "Other" (another account in their name). */
  @Column(name = "payout_account_type")
  private String payoutAccountType;

  @Column(name = "payout_bank_code")
  private String payoutBankCode;

  @Column(name = "payout_bank_name")
  private String payoutBankName;

  @Column(name = "payout_account_number")
  private String payoutAccountNumber;

  @Column(name = "payout_account_name")
  private String payoutAccountName;

  /** True when the bank confirmed the account name; false when it could not be checked. */
  @Column(name = "payout_name_verified")
  private Boolean payoutNameVerified;

  /** "Online", "Cheque" or "Import". */
  @Column(name = "disbursement_method")
  private String disbursementMethod;

  @Column(name = "cheque_url")
  private String chequeUrl;

  @Column(name = "cheque_number")
  private String chequeNumber;

  @Column(name = "disbursed_by_name")
  private String disbursedByName;

  protected LoanRecord() {
    // JPA
  }

  public LoanRecord(
      String cooperativeId,
      String memberId,
      UUID loanTypeId,
      BigDecimal amount,
      BigDecimal interestRate,
      int durationMonths,
      int numberOfRepayments,
      BigDecimal monthlyRepayment,
      BigDecimal totalRepayment,
      String guarantorId,
      String guarantorName,
      String status,
      String loanMode) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.loanTypeId = loanTypeId;
    this.amount = amount;
    this.interestRate = interestRate;
    this.durationMonths = durationMonths;
    this.numberOfRepayments = numberOfRepayments;
    this.monthlyRepayment = monthlyRepayment;
    this.totalRepayment = totalRepayment;
    this.guarantorId = guarantorId;
    this.guarantorName = guarantorName;
    this.loanDate = LocalDate.now();
    this.status = status;
    this.repaymentsMade = 0;
    this.loanMode = loanMode;
    this.createdAt = LocalDateTime.now();
    this.requestedAmount = amount;
    this.requestedMonths = durationMonths;
  }

  /** New live terms: an approver's suggestion. The requested figures stay as they were. */
  public void applyTerms(
      BigDecimal amount, int months, BigDecimal interestRate, BigDecimal monthlyRepayment, BigDecimal totalRepayment) {
    this.amount = amount;
    this.durationMonths = months;
    this.numberOfRepayments = months;
    this.interestRate = interestRate;
    this.monthlyRepayment = monthlyRepayment;
    this.totalRepayment = totalRepayment;
  }

  public void setApprovalStep(Integer approvalStep) {
    this.approvalStep = approvalStep;
  }

  /** The borrower accepts the approved terms and says where the money goes. */
  public void acceptOffer(
      String accountType, String bankCode, String bankName, String accountNumber, String accountName, boolean verified) {
    this.payoutAccountType = accountType;
    this.payoutBankCode = bankCode;
    this.payoutBankName = bankName;
    this.payoutAccountNumber = accountNumber;
    this.payoutAccountName = accountName;
    this.payoutNameVerified = verified;
    this.offerRespondedAt = LocalDateTime.now();
  }

  public void declineOffer() {
    this.offerRespondedAt = LocalDateTime.now();
  }

  public void setDisbursementDetails(String method, String chequeUrl, String chequeNumber, String disbursedByName) {
    this.disbursementMethod = method;
    this.chequeUrl = chequeUrl;
    this.chequeNumber = chequeNumber;
    this.disbursedByName = disbursedByName;
  }

  public BigDecimal getRequestedAmount() {
    return requestedAmount != null ? requestedAmount : amount;
  }

  public int getRequestedMonths() {
    return requestedMonths != null ? requestedMonths : durationMonths;
  }

  public Integer getApprovalStep() {
    return approvalStep;
  }

  public LocalDateTime getOfferRespondedAt() {
    return offerRespondedAt;
  }

  public String getPayoutAccountType() {
    return payoutAccountType;
  }

  public String getPayoutBankCode() {
    return payoutBankCode;
  }

  public String getPayoutBankName() {
    return payoutBankName;
  }

  public String getPayoutAccountNumber() {
    return payoutAccountNumber;
  }

  public String getPayoutAccountName() {
    return payoutAccountName;
  }

  public Boolean getPayoutNameVerified() {
    return payoutNameVerified;
  }

  public String getDisbursementMethod() {
    return disbursementMethod;
  }

  public String getChequeUrl() {
    return chequeUrl;
  }

  public String getChequeNumber() {
    return chequeNumber;
  }

  public String getDisbursedByName() {
    return disbursedByName;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void incrementRepaymentsMade() {
    this.repaymentsMade++;
  }

  public void setGuarantorAcceptedAt(LocalDateTime guarantorAcceptedAt) {
    this.guarantorAcceptedAt = guarantorAcceptedAt;
  }

  public void setGuarantorDocumentUrl(String guarantorDocumentUrl) {
    this.guarantorDocumentUrl = guarantorDocumentUrl;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public void markDisbursed(String reference, String transferStatus, BigDecimal netAmount) {
    this.disbursementReference = reference;
    this.disbursementStatus = transferStatus;
    this.disbursedAmount = netAmount;
    this.disbursedAt = LocalDateTime.now();
  }

  /** An existing loan brought in by an admin's import: it was paid out offline on {@code start},
   * so it skips the approval chain and payout, and starts with {@code repaymentsMade} already
   * paid. */
  void markImported(LocalDate start, int repaymentsMade, String status) {
    this.loanDate = start;
    this.disbursedAt = start.atStartOfDay();
    this.disbursedAmount = amount;
    this.disbursementStatus = "Imported";
    this.disbursementReference = "import-" + id;
    this.disbursementMethod = "Import";
    this.repaymentsMade = repaymentsMade;
    this.status = status;
  }

  public String getLoanMode() {
    return loanMode;
  }

  public boolean isWithin() {
    return LoanEligibility.WITHIN.equals(loanMode);
  }

  public String getDisbursementReference() {
    return disbursementReference;
  }

  public String getDisbursementStatus() {
    return disbursementStatus;
  }

  public BigDecimal getDisbursedAmount() {
    return disbursedAmount;
  }

  public LocalDateTime getDisbursedAt() {
    return disbursedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getMemberId() {
    return memberId;
  }

  public UUID getLoanTypeId() {
    return loanTypeId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public BigDecimal getInterestRate() {
    return interestRate;
  }

  public int getDurationMonths() {
    return durationMonths;
  }

  public int getNumberOfRepayments() {
    return numberOfRepayments;
  }

  public BigDecimal getMonthlyRepayment() {
    return monthlyRepayment;
  }

  public BigDecimal getTotalRepayment() {
    return totalRepayment;
  }

  public String getGuarantorId() {
    return guarantorId;
  }

  public String getGuarantorName() {
    return guarantorName;
  }

  public LocalDate getLoanDate() {
    return loanDate;
  }

  public String getStatus() {
    return status;
  }

  public int getRepaymentsMade() {
    return repaymentsMade;
  }

  public String getGuarantorDocumentUrl() {
    return guarantorDocumentUrl;
  }

  public LocalDateTime getGuarantorAcceptedAt() {
    return guarantorAcceptedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
