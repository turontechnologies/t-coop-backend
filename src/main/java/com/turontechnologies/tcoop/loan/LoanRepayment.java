package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One real installment payment against a loan — the borrower's own Paystack checkout or an
 * admin's manual entry after receiving payment offline. Always exactly one fixed installment;
 * see this table's own comment in V35 for why partial amounts aren't supported. */
@Entity
@Table(name = "loan_repayments")
public class LoanRepayment {

  @Id private UUID id;

  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "installment_number")
  private int installmentNumber;

  private BigDecimal amount;
  private String method;

  @Column(name = "transaction_id")
  private String transactionId;

  @Column(name = "repayment_date")
  private LocalDate repaymentDate;

  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected LoanRepayment() {
    // JPA
  }

  public LoanRepayment(
      UUID loanId,
      String cooperativeId,
      String memberId,
      int installmentNumber,
      BigDecimal amount,
      String method,
      String transactionId) {
    this.id = UUID.randomUUID();
    this.loanId = loanId;
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.installmentNumber = installmentNumber;
    this.amount = amount;
    this.method = method;
    this.transactionId = transactionId;
    this.repaymentDate = LocalDate.now();
    this.status = "Success";
    this.createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
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

  public String getMethod() {
    return method;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public LocalDate getRepaymentDate() {
    return repaymentDate;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
