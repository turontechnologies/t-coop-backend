package com.turontechnologies.tcoop.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * The bridge between a self-service payment's "initialize" and "confirm" steps — see
 * V9__subscription_payment_intents.sql. Never trust a client-supplied amount when confirming a
 * payment; look up what we ourselves decided to charge, here, by reference.
 */
@Entity
@Table(name = "subscription_payment_intents")
public class SubscriptionPaymentIntent {

  @Id private String reference;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private BigDecimal amount;
  private String cycle;
  private String gateway;
  private String status;

  @Column(name = "duration_in_days")
  private int durationInDays;

  protected SubscriptionPaymentIntent() {
    // JPA
  }

  public SubscriptionPaymentIntent(
      String reference,
      String cooperativeId,
      BigDecimal amount,
      String cycle,
      int durationInDays,
      String gateway) {
    this.reference = reference;
    this.cooperativeId = cooperativeId;
    this.amount = amount;
    this.cycle = cycle;
    this.durationInDays = durationInDays;
    this.gateway = gateway;
    this.status = "Pending";
  }

  public String getReference() {
    return reference;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCycle() {
    return cycle;
  }

  public int getDurationInDays() {
    return durationInDays;
  }

  public String getGateway() {
    return gateway;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
