package com.turontechnologies.tcoop.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The bridge between a member's self-service deposit "initialize" and "confirm" steps — mirrors
 * {@code SubscriptionPaymentIntent} exactly. Never trust a client-supplied amount when confirming
 * a deposit; look up what we ourselves decided to charge, here, by reference.
 */
@Entity
@Table(name = "savings_payment_intents")
public class SavingsPaymentIntent {

  @Id private String reference;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "savings_type_id")
  private UUID savingsTypeId;

  private BigDecimal amount;
  private String status;

  protected SavingsPaymentIntent() {
    // JPA
  }

  public SavingsPaymentIntent(
      String reference, String cooperativeId, String memberId, UUID savingsTypeId, BigDecimal amount) {
    this.reference = reference;
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.savingsTypeId = savingsTypeId;
    this.amount = amount;
    this.status = "Pending";
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

  public UUID getSavingsTypeId() {
    return savingsTypeId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
