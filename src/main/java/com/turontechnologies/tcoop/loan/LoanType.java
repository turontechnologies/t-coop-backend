package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One loan product a co-operative offers its members (e.g. "Emergency Loan") — mirrors
 * SavingsType's role for the parallel Loans oversight feature. */
@Entity
@Table(name = "loan_types")
public class LoanType {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String name;

  @Column(name = "eligibility_percent")
  private BigDecimal eligibilityPercent;

  @Column(name = "duration_months")
  private int durationMonths;

  @Column(name = "max_amount")
  private BigDecimal maxAmount;

  @Column(name = "repayment_interval")
  private String repaymentInterval;

  @Column(name = "number_of_installments")
  private int numberOfInstallments;

  @Column(name = "interest_type")
  private String interestType;

  @Column(name = "interest_amount")
  private BigDecimal interestAmount;

  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected LoanType() {
    // JPA
  }

  public LoanType(
      String cooperativeId,
      String name,
      BigDecimal eligibilityPercent,
      int durationMonths,
      BigDecimal maxAmount,
      String repaymentInterval,
      int numberOfInstallments,
      String interestType,
      BigDecimal interestAmount) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.name = name;
    this.eligibilityPercent = eligibilityPercent;
    this.durationMonths = durationMonths;
    this.maxAmount = maxAmount;
    this.repaymentInterval = repaymentInterval;
    this.numberOfInstallments = numberOfInstallments;
    this.interestType = interestType;
    this.interestAmount = interestAmount;
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

  public BigDecimal getEligibilityPercent() {
    return eligibilityPercent;
  }

  public int getDurationMonths() {
    return durationMonths;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public String getRepaymentInterval() {
    return repaymentInterval;
  }

  public int getNumberOfInstallments() {
    return numberOfInstallments;
  }

  public String getInterestType() {
    return interestType;
  }

  public BigDecimal getInterestAmount() {
    return interestAmount;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
