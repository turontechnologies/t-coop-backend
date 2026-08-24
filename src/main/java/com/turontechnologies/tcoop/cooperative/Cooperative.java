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

  @Column(name = "withdrawal_fee_percent")
  private BigDecimal withdrawalFeePercent;

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

  protected Cooperative() {
    // JPA
  }

  /** Creates a new co-operative — id/status/currency/subscriptionFee follow the platform's
   * onboarding defaults (see CooperativeController), everything else comes from the request. */
  public Cooperative(
      String id,
      String name,
      String adminName,
      String contactEmail,
      String contactPhone,
      String address,
      String country,
      String state,
      String city) {
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
    this.currency = "NGN";
    this.subscriptionFee = new BigDecimal("150000");
    this.withdrawalFeePercent = BigDecimal.ZERO;
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

  public BigDecimal getWithdrawalFeePercent() {
    return withdrawalFeePercent;
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

  public void setWithdrawalFeePercent(BigDecimal withdrawalFeePercent) {
    this.withdrawalFeePercent = withdrawalFeePercent;
  }

  public void updateBankAccount(String bankCode, String accountNumber, String accountName) {
    this.bankCode = bankCode;
    this.accountNumber = accountNumber;
    this.accountName = accountName;
  }
}
