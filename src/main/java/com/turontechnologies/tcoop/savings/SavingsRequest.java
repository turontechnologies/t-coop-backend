package com.turontechnologies.tcoop.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A member's request to deposit or withdraw against their savings, awaiting admin decision —
 * maps the {@code savings_requests} table from V1, unused by any Java code until now. Only
 * withdrawals are created through the API today (see {@code SavingsSelfServiceController}); the
 * {@code Deposit} request_type value exists in the schema but nothing currently creates one,
 * since a deposit either goes straight through Paystack (auto-approved by verification) or is
 * entered directly by an admin via "Upload Teller" — neither needs a pending-approval step.
 */
@Entity
@Table(name = "savings_requests")
public class SavingsRequest {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "request_type")
  private String requestType;

  @Column(name = "savings_type_id")
  private UUID savingsTypeId;

  private BigDecimal amount;
  private String note;
  private String status;

  @Column(name = "fee_percent")
  private BigDecimal feePercent;

  @Column(name = "fee_amount")
  private BigDecimal feeAmount;

  @Column(name = "net_amount")
  private BigDecimal netAmount;

  @Column(name = "requested_at")
  private LocalDateTime requestedAt;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  protected SavingsRequest() {
    // JPA
  }

  public SavingsRequest(
      String cooperativeId,
      String memberId,
      String requestType,
      UUID savingsTypeId,
      BigDecimal amount,
      String note,
      BigDecimal feePercent,
      BigDecimal feeAmount,
      BigDecimal netAmount) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.requestType = requestType;
    this.savingsTypeId = savingsTypeId;
    this.amount = amount;
    this.note = note;
    this.status = "Pending";
    this.feePercent = feePercent;
    this.feeAmount = feeAmount;
    this.netAmount = netAmount;
    this.requestedAt = LocalDateTime.now();
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

  public String getRequestType() {
    return requestType;
  }

  public UUID getSavingsTypeId() {
    return savingsTypeId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getNote() {
    return note;
  }

  public String getStatus() {
    return status;
  }

  public BigDecimal getFeePercent() {
    return feePercent;
  }

  public BigDecimal getFeeAmount() {
    return feeAmount;
  }

  public BigDecimal getNetAmount() {
    return netAmount;
  }

  public LocalDateTime getRequestedAt() {
    return requestedAt;
  }

  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  public void resolve(String status) {
    this.status = status;
    this.resolvedAt = LocalDateTime.now();
  }
}
