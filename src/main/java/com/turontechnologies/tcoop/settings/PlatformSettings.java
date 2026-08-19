package com.turontechnologies.tcoop.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Singleton row (id always 1) backing three of the super-admin Settings tabs: Fees & Charges,
 * Payment Settings' Account Details, and Integrations. One platform, one settings blob — see
 * schema-design.md for the original reasoning (still applies with the new columns from V6).
 */
@Entity
@Table(name = "platform_fee_settings")
public class PlatformSettings {

  @Id private Integer id;

  @Column(name = "savings_charge_type")
  private String savingsChargeType;

  @Column(name = "savings_charge_amount")
  private BigDecimal savingsChargeAmount;

  @Column(name = "loans_charge_type")
  private String loansChargeType;

  @Column(name = "loans_charge_amount")
  private BigDecimal loansChargeAmount;

  @Column(name = "withdrawal_fee_percent")
  private BigDecimal withdrawalFeePercent;

  @Column(name = "collection_bank_code")
  private String collectionBankCode;

  @Column(name = "collection_account_number")
  private String collectionAccountNumber;

  @Column(name = "collection_account_name")
  private String collectionAccountName;

  @Column(name = "paystack_enabled")
  private boolean paystackEnabled;

  @Column(name = "paystack_public_key")
  private String paystackPublicKey;

  @Column(name = "paystack_secret_key")
  private String paystackSecretKey;

  @Column(name = "paystack_webhook_secret")
  private String paystackWebhookSecret;

  @Column(name = "flutterwave_enabled")
  private boolean flutterwaveEnabled;

  @Column(name = "flutterwave_public_key")
  private String flutterwavePublicKey;

  @Column(name = "flutterwave_secret_key")
  private String flutterwaveSecretKey;

  @Column(name = "flutterwave_encryption_key")
  private String flutterwaveEncryptionKey;

  @Column(name = "opay_enabled")
  private boolean opayEnabled;

  @Column(name = "opay_public_key")
  private String opayPublicKey;

  @Column(name = "opay_secret_key")
  private String opaySecretKey;

  @Column(name = "opay_merchant_id")
  private String opayMerchantId;

  protected PlatformSettings() {
    // JPA
  }

  public Integer getId() {
    return id;
  }

  public String getSavingsChargeType() {
    return savingsChargeType;
  }

  public BigDecimal getSavingsChargeAmount() {
    return savingsChargeAmount;
  }

  public String getLoansChargeType() {
    return loansChargeType;
  }

  public BigDecimal getLoansChargeAmount() {
    return loansChargeAmount;
  }

  public BigDecimal getWithdrawalFeePercent() {
    return withdrawalFeePercent;
  }

  public String getCollectionBankCode() {
    return collectionBankCode;
  }

  public String getCollectionAccountNumber() {
    return collectionAccountNumber;
  }

  public String getCollectionAccountName() {
    return collectionAccountName;
  }

  public boolean isPaystackEnabled() {
    return paystackEnabled;
  }

  public String getPaystackPublicKey() {
    return paystackPublicKey;
  }

  public String getPaystackSecretKey() {
    return paystackSecretKey;
  }

  public String getPaystackWebhookSecret() {
    return paystackWebhookSecret;
  }

  public boolean isFlutterwaveEnabled() {
    return flutterwaveEnabled;
  }

  public String getFlutterwavePublicKey() {
    return flutterwavePublicKey;
  }

  public String getFlutterwaveSecretKey() {
    return flutterwaveSecretKey;
  }

  public String getFlutterwaveEncryptionKey() {
    return flutterwaveEncryptionKey;
  }

  public boolean isOpayEnabled() {
    return opayEnabled;
  }

  public String getOpayPublicKey() {
    return opayPublicKey;
  }

  public String getOpaySecretKey() {
    return opaySecretKey;
  }

  public String getOpayMerchantId() {
    return opayMerchantId;
  }

  public void updateFees(
      String savingsChargeType,
      BigDecimal savingsChargeAmount,
      String loansChargeType,
      BigDecimal loansChargeAmount,
      BigDecimal withdrawalFeePercent) {
    this.savingsChargeType = savingsChargeType;
    this.savingsChargeAmount = savingsChargeAmount;
    this.loansChargeType = loansChargeType;
    this.loansChargeAmount = loansChargeAmount;
    this.withdrawalFeePercent = withdrawalFeePercent;
  }

  public void updateCollectionAccount(
      String collectionBankCode, String collectionAccountNumber, String collectionAccountName) {
    this.collectionBankCode = collectionBankCode;
    this.collectionAccountNumber = collectionAccountNumber;
    this.collectionAccountName = collectionAccountName;
  }

  public void updateIntegrations(
      boolean paystackEnabled,
      String paystackPublicKey,
      String paystackSecretKey,
      String paystackWebhookSecret,
      boolean flutterwaveEnabled,
      String flutterwavePublicKey,
      String flutterwaveSecretKey,
      String flutterwaveEncryptionKey,
      boolean opayEnabled,
      String opayPublicKey,
      String opaySecretKey,
      String opayMerchantId) {
    this.paystackEnabled = paystackEnabled;
    this.paystackPublicKey = paystackPublicKey;
    this.paystackSecretKey = paystackSecretKey;
    this.paystackWebhookSecret = paystackWebhookSecret;
    this.flutterwaveEnabled = flutterwaveEnabled;
    this.flutterwavePublicKey = flutterwavePublicKey;
    this.flutterwaveSecretKey = flutterwaveSecretKey;
    this.flutterwaveEncryptionKey = flutterwaveEncryptionKey;
    this.opayEnabled = opayEnabled;
    this.opayPublicKey = opayPublicKey;
    this.opaySecretKey = opaySecretKey;
    this.opayMerchantId = opayMerchantId;
  }
}
