package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One loan application fee payment (V49). Started as Pending when the checkout opens, Paid once
 * the gateway confirms it server-side, Used when it's spent on an application.
 */
@Entity
@Table(name = "loan_application_fees")
public class LoanApplicationFee {

  public static final String PENDING = "Pending";
  public static final String PAID = "Paid";
  public static final String USED = "Used";
  public static final String FAILED = "Failed";

  @Id private String reference;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  private BigDecimal amount;
  private String gateway;

  @Column(name = "key_owner")
  private String keyOwner;

  private String status;

  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  /** The loan type this fee was paid for, when fees are set per type; null for a general fee. */
  @Column(name = "loan_type_id")
  private UUID loanTypeId;

  public UUID getLoanTypeId() {
    return loanTypeId;
  }

  public void setLoanTypeId(UUID loanTypeId) {
    this.loanTypeId = loanTypeId;
  }

  protected LoanApplicationFee() {
    // JPA
  }

  public LoanApplicationFee(
      String reference, String cooperativeId, String memberId, BigDecimal amount, String gateway, String keyOwner) {
    this.reference = reference;
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.amount = amount;
    this.gateway = gateway;
    this.keyOwner = keyOwner;
    this.status = PENDING;
    this.createdAt = LocalDateTime.now();
  }

  public void markPaid() {
    this.status = PAID;
    this.paidAt = LocalDateTime.now();
  }

  public void markFailed() {
    this.status = FAILED;
  }

  public void markUsed(UUID loanId) {
    this.status = USED;
    this.loanId = loanId;
    this.usedAt = LocalDateTime.now();
  }

  public String getReference() {
    return reference;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getMemberId() {
    return memberId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getGateway() {
    return gateway;
  }

  public String getKeyOwner() {
    return keyOwner;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }
}
