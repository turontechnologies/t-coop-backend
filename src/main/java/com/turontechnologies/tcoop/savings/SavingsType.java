package com.turontechnologies.tcoop.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One savings product a co-operative offers its members (e.g. "Basic Savings"), with the
 * min/max contribution band super admin oversight displays. Nothing auto-creates one of these —
 * a co-op has zero savings types until someone deliberately configures one (no management UI
 * for that yet; the row shape already supports it). Per-co-op configurable data, not a
 * hardcoded global enum, unlike the frontend's old SAVINGS_TYPES mock constant it mirrors.
 */
@Entity
@Table(name = "savings_types")
public class SavingsType {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String name;

  @Column(name = "min_amount")
  private BigDecimal minAmount;

  @Column(name = "max_amount")
  private BigDecimal maxAmount;

  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected SavingsType() {
    // JPA
  }

  public SavingsType(
      String cooperativeId, String name, BigDecimal minAmount, BigDecimal maxAmount) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.name = name;
    this.minAmount = minAmount;
    this.maxAmount = maxAmount;
    this.status = "Active";
    this.createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void update(String name, BigDecimal minAmount, BigDecimal maxAmount) {
    this.name = name;
    this.minAmount = minAmount;
    this.maxAmount = maxAmount;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
