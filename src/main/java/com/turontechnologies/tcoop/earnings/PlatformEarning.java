package com.turontechnologies.tcoop.earnings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One fee the platform earned from a co-operative (V55). */
@Entity
@Table(name = "platform_earnings")
public class PlatformEarning {

  public static final String SAVINGS_CHARGE = "SavingsCharge";
  public static final String LOAN_CHARGE = "LoanCharge";
  public static final String WITHDRAWAL_FEE = "WithdrawalFee";
  public static final String LOAN_APPLICATION_FEE = "LoanApplicationFee";

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  private String source;

  /** What the fee was worked out on: the deposit, loan, withdrawal or application fee. */
  @Column(name = "base_amount")
  private BigDecimal baseAmount;

  /** The platform's share. */
  private BigDecimal amount;

  /** The transaction it came from; unique per source, so a fee is never recorded twice. */
  private String reference;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected PlatformEarning() {
    // JPA
  }

  public PlatformEarning(
      String cooperativeId,
      String memberId,
      String source,
      BigDecimal baseAmount,
      BigDecimal amount,
      String reference) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.source = source;
    this.baseAmount = baseAmount;
    this.amount = amount;
    this.reference = reference;
    this.createdAt = LocalDateTime.now();
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

  public String getSource() {
    return source;
  }

  public BigDecimal getBaseAmount() {
    return baseAmount;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getReference() {
    return reference;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
