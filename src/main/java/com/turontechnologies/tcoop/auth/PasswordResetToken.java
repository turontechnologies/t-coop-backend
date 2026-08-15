package com.turontechnologies.tcoop.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

  @Id private UUID id;

  private String email;

  @Column(name = "otp_hash")
  private String otpHash;

  @Column(name = "reset_token")
  private String resetToken;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  private boolean used;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected PasswordResetToken() {
    // JPA
  }

  public PasswordResetToken(String email, String otpHash, LocalDateTime expiresAt) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.otpHash = otpHash;
    this.expiresAt = expiresAt;
    this.used = false;
    this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getOtpHash() {
    return otpHash;
  }

  public String getResetToken() {
    return resetToken;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public boolean isUsed() {
    return used;
  }

  /** Called once the OTP has been verified — issues the token the reset-password step needs. */
  public void markOtpVerified(String resetToken) {
    this.resetToken = resetToken;
  }

  public void markUsed() {
    this.used = true;
  }
}
