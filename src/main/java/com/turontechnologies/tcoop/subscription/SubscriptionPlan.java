package com.turontechnologies.tcoop.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A super-admin-managed subscription price: how much a co-op pays and for how long, for either
 * its first ever payment ("New Subscription") or any payment after ("Renewal"). Freely
 * addable/editable/deletable from Payment Settings -> Subscription Plans — {@code durationInDays}
 * is the flexible unit (not a fixed Weekly/Monthly/Quarterly/Yearly enum) so a plan can be any
 * length the super admin wants.
 */
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

  @Id private UUID id;

  private String type;
  private String label;

  @Column(name = "duration_in_days")
  private int durationInDays;

  private BigDecimal amount;
  private String status;

  protected SubscriptionPlan() {
    // JPA
  }

  public SubscriptionPlan(
      String type, String label, int durationInDays, BigDecimal amount, String status) {
    this.id = UUID.randomUUID();
    this.type = type;
    this.label = label;
    this.durationInDays = durationInDays;
    this.amount = amount;
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getLabel() {
    return label;
  }

  public int getDurationInDays() {
    return durationInDays;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getStatus() {
    return status;
  }

  public void update(String label, int durationInDays, BigDecimal amount, String status) {
    this.label = label;
    this.durationInDays = durationInDays;
    this.amount = amount;
    this.status = status;
  }
}
