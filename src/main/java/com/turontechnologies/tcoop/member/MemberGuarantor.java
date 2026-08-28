package com.turontechnologies.tcoop.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** One guarantor named when a member was added — a real row with a real accept/decline status,
 * not just a name on file. Created "Pending" with an accept token; a guarantor only actually
 * counts once they click through their email and accept (see GuarantorInviteController, the
 * public unauthenticated side of this — the guarantor never has their own T-Coop account). */
@Entity
@Table(name = "member_guarantors")
public class MemberGuarantor {

  @Id private UUID id;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String name;
  private String email;
  private String phone;
  private String status;

  @Column(name = "accept_token")
  private String acceptToken;

  @Column(name = "accept_token_expires_at")
  private LocalDateTime acceptTokenExpiresAt;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected MemberGuarantor() {
    // JPA
  }

  public MemberGuarantor(String memberId, String cooperativeId, String name, String email, String phone) {
    this.id = UUID.randomUUID();
    this.memberId = memberId;
    this.cooperativeId = cooperativeId;
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.status = "Pending";
    this.createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getStatus() {
    return status;
  }

  public String getAcceptToken() {
    return acceptToken;
  }

  public LocalDateTime getAcceptTokenExpiresAt() {
    return acceptTokenExpiresAt;
  }

  public LocalDateTime getRespondedAt() {
    return respondedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setAcceptToken(String acceptToken, LocalDateTime expiresAt) {
    this.acceptToken = acceptToken;
    this.acceptTokenExpiresAt = expiresAt;
  }

  public void respond(String status) {
    this.status = status;
    this.respondedAt = LocalDateTime.now();
  }
}
