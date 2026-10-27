package com.turontechnologies.tcoop.coopgateway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One co-op's own Paystack/Flutterwave credentials — the co-op-scoped counterpart to the
 * platform's integration fields on {@code PlatformSettings}. See V39 for why it's its own table.
 */
@Entity
@Table(name = "cooperative_payment_gateways")
public class CooperativePaymentGateway {

  @Id
  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "paystack_enabled")
  private boolean paystackEnabled;

  @Column(name = "paystack_public_key")
  private String paystackPublicKey;

  @Column(name = "paystack_secret_key")
  private String paystackSecretKey;

  @Column(name = "flutterwave_enabled")
  private boolean flutterwaveEnabled;

  @Column(name = "flutterwave_public_key")
  private String flutterwavePublicKey;

  @Column(name = "flutterwave_secret_key")
  private String flutterwaveSecretKey;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  protected CooperativePaymentGateway() {
    // JPA
  }

  public CooperativePaymentGateway(String cooperativeId) {
    this.cooperativeId = cooperativeId;
    this.updatedAt = LocalDateTime.now();
  }

  public void updatePaystack(boolean enabled, String publicKey, String secretKey) {
    this.paystackEnabled = enabled;
    this.paystackPublicKey = publicKey;
    this.paystackSecretKey = secretKey;
    this.updatedAt = LocalDateTime.now();
  }

  public void updateFlutterwave(boolean enabled, String publicKey, String secretKey) {
    this.flutterwaveEnabled = enabled;
    this.flutterwavePublicKey = publicKey;
    this.flutterwaveSecretKey = secretKey;
    this.updatedAt = LocalDateTime.now();
  }

  public String getCooperativeId() {
    return cooperativeId;
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

  public boolean isFlutterwaveEnabled() {
    return flutterwaveEnabled;
  }

  public String getFlutterwavePublicKey() {
    return flutterwavePublicKey;
  }

  public String getFlutterwaveSecretKey() {
    return flutterwaveSecretKey;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
