package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A borrower telling the co-op "I repaid by bank transfer" (V57). It waits, Pending, until the
 * admin or loan staff check their bank and confirm it — only then does it become real
 * {@link LoanRepayment} rows (method "Bank Transfer") and count towards the loan. The amount is
 * always whole installments, computed server-side, like every other repayment in this app.
 */
@Entity
@Table(name = "loan_repayment_claims")
public class LoanRepaymentClaim {

  public static final String PENDING = "Pending";
  public static final String CONFIRMED = "Confirmed";
  public static final String REJECTED = "Rejected";

  @Id private UUID id;

  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  private int installments;
  private BigDecimal amount;

  @Column(name = "receipt_url")
  private String receiptUrl;

  @Column(name = "transfer_reference")
  private String transferReference;

  @Column(name = "paid_on")
  private LocalDate paidOn;

  private String note;
  private String status;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "decided_by_name")
  private String decidedByName;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  protected LoanRepaymentClaim() {
    // JPA
  }

  public LoanRepaymentClaim(
      UUID loanId,
      String cooperativeId,
      String memberId,
      int installments,
      BigDecimal amount,
      String receiptUrl,
      String transferReference,
      LocalDate paidOn,
      String note) {
    this.id = UUID.randomUUID();
    this.loanId = loanId;
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.installments = installments;
    this.amount = amount;
    this.receiptUrl = receiptUrl;
    this.transferReference = blankToNull(transferReference);
    this.paidOn = paidOn;
    this.note = blankToNull(note);
    this.status = PENDING;
    this.submittedAt = LocalDateTime.now();
  }

  public void confirm(String byName) {
    this.status = CONFIRMED;
    this.decidedAt = LocalDateTime.now();
    this.decidedByName = byName;
  }

  public void reject(String byName, String reason) {
    this.status = REJECTED;
    this.decidedAt = LocalDateTime.now();
    this.decidedByName = byName;
    this.rejectionReason = blankToNull(reason);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getId() {
    return id;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getMemberId() {
    return memberId;
  }

  public int getInstallments() {
    return installments;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getReceiptUrl() {
    return receiptUrl;
  }

  public String getTransferReference() {
    return transferReference;
  }

  public LocalDate getPaidOn() {
    return paidOn;
  }

  public String getNote() {
    return note;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public LocalDateTime getDecidedAt() {
    return decidedAt;
  }

  public String getDecidedByName() {
    return decidedByName;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }
}
