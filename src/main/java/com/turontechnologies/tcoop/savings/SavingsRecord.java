package com.turontechnologies.tcoop.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One deposit or withdrawal against a member's savings — negative {@code amount} for a
 * withdrawal, same as a Subscription's or Loan's ledger convention elsewhere in this codebase.
 * Currently populated only by seed data (V3) — the real admin "Upload Teller" / member Paystack
 * flows that create these still live in the frontend's mock store, not this backend, so a
 * co-operative onboarded after the seed migration legitimately has zero records until those are
 * cut over. Super-admin oversight (SavingsController) is read-only against whatever exists here.
 */
@Entity
@Table(name = "savings_records")
public class SavingsRecord {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "savings_type_id")
  private UUID savingsTypeId;

  private BigDecimal amount;

  @Column(name = "balance_after")
  private BigDecimal balanceAfter;

  private String method;

  @Column(name = "transaction_id")
  private String transactionId;

  @Column(name = "record_date")
  private LocalDate recordDate;

  private String status;

  @Column(name = "receipt_url")
  private String receiptUrl;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected SavingsRecord() {
    // JPA
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

  public UUID getSavingsTypeId() {
    return savingsTypeId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public BigDecimal getBalanceAfter() {
    return balanceAfter;
  }

  public String getMethod() {
    return method;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public LocalDate getRecordDate() {
    return recordDate;
  }

  public String getStatus() {
    return status;
  }

  public String getReceiptUrl() {
    return receiptUrl;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
