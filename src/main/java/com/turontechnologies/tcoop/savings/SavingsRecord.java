package com.turontechnologies.tcoop.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

  @Column(name = "record_date")
  private LocalDate recordDate;

  private String status;

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

  public LocalDate getRecordDate() {
    return recordDate;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
