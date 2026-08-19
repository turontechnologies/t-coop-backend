package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One loan a member took out — currently populated only by seed data (V3), same honest
 * limitation as SavingsRecord: the real disbursement/guarantor-approval flow still lives in the
 * frontend's mock store, not this backend. Super-admin oversight (LoanController) is read-only
 * against whatever exists here. */
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

  @Column(name = "interest_rate")
  private BigDecimal interestRate;

  @Column(name = "duration_months")
  private int durationMonths;

  @Column(name = "number_of_repayments")
  private int numberOfRepayments;

  @Column(name = "monthly_repayment")
  private BigDecimal monthlyRepayment;

  @Column(name = "total_repayment")
  private BigDecimal totalRepayment;

  @Column(name = "guarantor_id")
  private String guarantorId;

  @Column(name = "guarantor_name")
  private String guarantorName;

  @Column(name = "loan_date")
  private LocalDate loanDate;

  private String status;

  @Column(name = "repayments_made")
  private int repaymentsMade;

  @Column(name = "guarantor_document_url")
  private String guarantorDocumentUrl;

  @Column(name = "guarantor_accepted_at")
  private LocalDateTime guarantorAcceptedAt;

  @Column(name = "rejection_reason")
  private String rejectionReason;

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

  public BigDecimal getInterestRate() {
    return interestRate;
  }

  public int getDurationMonths() {
    return durationMonths;
  }

  public int getNumberOfRepayments() {
    return numberOfRepayments;
  }

  public BigDecimal getMonthlyRepayment() {
    return monthlyRepayment;
  }

  public BigDecimal getTotalRepayment() {
    return totalRepayment;
  }

  public String getGuarantorId() {
    return guarantorId;
  }

  public String getGuarantorName() {
    return guarantorName;
  }

  public LocalDate getLoanDate() {
    return loanDate;
  }

  public String getStatus() {
    return status;
  }

  public int getRepaymentsMade() {
    return repaymentsMade;
  }

  public String getGuarantorDocumentUrl() {
    return guarantorDocumentUrl;
  }

  public LocalDateTime getGuarantorAcceptedAt() {
    return guarantorAcceptedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
