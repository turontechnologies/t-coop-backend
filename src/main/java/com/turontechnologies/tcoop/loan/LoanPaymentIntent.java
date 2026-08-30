package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** The bridge between a borrower's own repayment "initialize" and "confirm" steps — mirrors
 * {@code SavingsPaymentIntent} exactly. */
@Entity
@Table(name = "loan_payment_intents")
public class LoanPaymentIntent {

  @Id private String reference;

  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "installment_number")
  private int installmentNumber;

  private BigDecimal amount;
  private String status;

  protected LoanPaymentIntent() {
    // JPA
  }

  public LoanPaymentIntent(
      String reference,
      UUID loanId,
      String cooperativeId,
      String memberId,
      int installmentNumber,
      BigDecimal amount) {
    this.reference = reference;
    this.loanId = loanId;
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.installmentNumber = installmentNumber;
    this.amount = amount;
    this.status = "Pending";
  }

  public String getReference() {
    return reference;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getMemberId() {
    return memberId;
  }

  public int getInstallmentNumber() {
    return installmentNumber;
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
