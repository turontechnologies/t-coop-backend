package com.turontechnologies.tcoop.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A single platform-subscription payment recorded by a super admin for one co-op. */
@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "payment_ref")
  private String paymentRef;

  @Column(name = "amount_paid")
  private BigDecimal amountPaid;

  private String method;

  @Column(name = "payment_date")
  private LocalDate paymentDate;

  private String narration;

  /** "New Subscription" (the co-op's first ever payment) or "Renewal" — set server-side. */
  private String type;

  /** The billing cycle this payment bought: Weekly/Monthly/Quarterly/Yearly. */
  private String cycle;

  private String status;

  /** What this specific payment extended the co-op's subscription to — captured at the time,
   * so a receipt re-downloaded later still shows what THAT payment bought, not whatever the
   * co-op's expiry happens to be now after later renewals. */
  @Column(name = "resulting_expires_at")
  private LocalDate resultingExpiresAt;

  protected SubscriptionPayment() {
    // JPA
  }

  public SubscriptionPayment(
      String cooperativeId,
      String paymentRef,
      BigDecimal amountPaid,
      String method,
      LocalDate paymentDate,
      String type,
      String cycle,
      String status,
      LocalDate resultingExpiresAt) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.paymentRef = paymentRef;
    this.amountPaid = amountPaid;
    this.method = method;
    this.paymentDate = paymentDate;
    this.narration = null;
    this.type = type;
    this.cycle = cycle;
    this.status = status;
    this.resultingExpiresAt = resultingExpiresAt;
  }

  public UUID getId() {
    return id;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getPaymentRef() {
    return paymentRef;
  }

  public BigDecimal getAmountPaid() {
    return amountPaid;
  }

  public String getMethod() {
    return method;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public String getNarration() {
    return narration;
  }

  public String getType() {
    return type;
  }

  public String getCycle() {
    return cycle;
  }

  public String getStatus() {
    return status;
  }

  public LocalDate getResultingExpiresAt() {
    return resultingExpiresAt;
  }
}
