package com.turontechnologies.tcoop.cooperative;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cooperatives")
public class Cooperative {

  @Id private String id;

  private String name;

  @Column(name = "admin_name")
  private String adminName;

  @Column(name = "contact_email")
  private String contactEmail;

  @Column(name = "contact_phone")
  private String contactPhone;

  private String address;
  private String country;
  private String state;
  private String city;
  private String status;
  private String currency;

  @Column(name = "subscription_fee")
  private BigDecimal subscriptionFee;

  @Column(name = "withdrawal_fee_amount")
  private BigDecimal withdrawalFeeAmount;

  /** Fixed or Percentage — decides how {@link #withdrawalFeeAmount} is applied, same shape as
   * PlatformSettings' savings/loans charge type. */
  @Column(name = "withdrawal_fee_type")
  private String withdrawalFeeType;

  @Column(name = "subscription_cycle")
  private String subscriptionCycle;

  @Column(name = "subscription_expires_at")
  private LocalDate subscriptionExpiresAt;

  /** The co-op's own receiving account — distinct from its admin's personal payout account
   * (Member.bankCode/accountNumber/accountName), which is a different, unrelated field. */
  @Column(name = "bank_code")
  private String bankCode;

  @Column(name = "account_number")
  private String accountNumber;

  @Column(name = "account_name")
  private String accountName;

  /** How this co-op's own admin generates the next member id (Members Directory "Add Member") —
   * defaults match the platform-wide co-op id format's own defaults, but each co-op can set its
   * own via Settings -> Co-operative -> Member ID Format, independent of every other co-op's. */
  @Column(name = "member_id_prefix")
  private String memberIdPrefix;

  @Column(name = "member_id_padding")
  private int memberIdPadding;

  /** NUMERIC (0-9), ALPHA (A-Z), or ALPHANUMERIC (0-9 then A-Z) — see
   * CooperativeController.nextGeneratedId for how this drives the base-N suffix encoding. */
  @Column(name = "member_id_type")
  private String memberIdType;

  /** Every new member needs at least this many guarantors (see CooperativeController.addMember —
   * also enforces that at least one of them is an existing member of this same co-op). Each
   * co-op's own admin sets this via Settings -> Co-operative -> Membership Rules. */
  @Column(name = "min_guarantors")
  private int minGuarantors;

  protected Cooperative() {
    // JPA
  }

  /** Creates a new co-operative — id/status/subscriptionFee follow the platform's onboarding
   * defaults (see CooperativeController); currency is the super admin's choice at creation time
   * (the co-op's own admin can change it later from their own Settings). */
  public Cooperative(
      String id,
      String name,
      String adminName,
      String contactEmail,
      String contactPhone,
      String address,
      String country,
      String state,
      String city,
      String currency) {
    this.id = id;
    this.name = name;
    this.adminName = adminName;
    this.contactEmail = contactEmail;
    this.contactPhone = contactPhone;
    this.address = address;
    this.country = country;
    this.state = state;
    this.city = city;
    this.status = "Active";
    this.currency = currency;
    this.subscriptionFee = new BigDecimal("150000");
    this.withdrawalFeeAmount = BigDecimal.ZERO;
    this.withdrawalFeeType = "Percentage";
    this.memberIdPrefix = "MB";
    this.memberIdPadding = 4;
    this.memberIdType = "NUMERIC";
    this.minGuarantors = 2;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getAdminName() {
    return adminName;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public String getContactPhone() {
    return contactPhone;
  }

  public String getAddress() {
    return address;
  }

  public String getCountry() {
    return country;
  }

  public String getState() {
    return state;
  }

  public String getCity() {
    return city;
  }

  public String getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getSubscriptionFee() {
    return subscriptionFee;
  }

  public BigDecimal getWithdrawalFeeAmount() {
    return withdrawalFeeAmount;
  }

  public String getWithdrawalFeeType() {
    return withdrawalFeeType;
  }

  public String getSubscriptionCycle() {
    return subscriptionCycle;
  }

  public LocalDate getSubscriptionExpiresAt() {
    return subscriptionExpiresAt;
  }

  public String getBankCode() {
    return bankCode;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getAccountName() {
    return accountName;
  }

  /**
   * A co-op can act on the platform only while this is true — null (never subscribed) and a
   * past date (lapsed) are treated identically. See SubscriptionGateFilter, the single
   * enforcement point for this rule across every endpoint.
   */
  public boolean hasActiveSubscription() {
    return subscriptionExpiresAt != null && !subscriptionExpiresAt.isBefore(LocalDate.now());
  }

  /** Applied whenever a subscription payment is recorded — extends/starts the current period. */
  public void recordSubscriptionPayment(String cycle, LocalDate newExpiresAt) {
    this.subscriptionCycle = cycle;
    this.subscriptionExpiresAt = newExpiresAt;
  }

  /** Editable profile fields — never touches id/status/currency/subscriptionFee. */
  public void updateDetails(
      String name,
      String adminName,
      String contactEmail,
      String contactPhone,
      String address,
      String country,
      String state,
      String city) {
    this.name = name;
    this.adminName = adminName;
    this.contactEmail = contactEmail;
    this.contactPhone = contactPhone;
    this.address = address;
    this.country = country;
    this.state = state;
    this.city = city;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public void setWithdrawalFee(BigDecimal withdrawalFeeAmount, String withdrawalFeeType) {
    this.withdrawalFeeAmount = withdrawalFeeAmount;
    this.withdrawalFeeType = withdrawalFeeType;
  }

  public void updateBankAccount(String bankCode, String accountNumber, String accountName) {
    this.bankCode = bankCode;
    this.accountNumber = accountNumber;
    this.accountName = accountName;
  }

  public String getMemberIdPrefix() {
    return memberIdPrefix;
  }

  public int getMemberIdPadding() {
    return memberIdPadding;
  }

  public String getMemberIdType() {
    return memberIdType;
  }

  public void updateMemberIdFormat(String memberIdPrefix, int memberIdPadding, String memberIdType) {
    this.memberIdPrefix = memberIdPrefix;
    this.memberIdPadding = memberIdPadding;
    this.memberIdType = memberIdType;
  }

  public int getMinGuarantors() {
    return minGuarantors;
  }

  public void setMinGuarantors(int minGuarantors) {
    this.minGuarantors = minGuarantors;
  }
}
