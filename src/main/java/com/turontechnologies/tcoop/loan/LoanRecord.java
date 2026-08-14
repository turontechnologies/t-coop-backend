package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_records")
public class LoanRecord {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "loan_type_id")
  private UUID loanTypeId;

  private BigDecimal amount;

  @Column(name = "loan_date")
  private LocalDate loanDate;

  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected LoanRecord() {
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

  public UUID getLoanTypeId() {
    return loanTypeId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDate getLoanDate() {
    return loanDate;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
