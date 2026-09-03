package com.turontechnologies.tcoop.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "push_tokens")
public class PushToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id")
  private String memberId;

  private String token;
  private String platform;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected PushToken() {
    // JPA
  }

  public PushToken(String memberId, String token, String platform) {
    this.memberId = memberId;
    this.token = token;
    this.platform = platform;
    this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  public Long getId() {
    return id;
  }

  public String getMemberId() {
    return memberId;
  }

  public void setMemberId(String memberId) {
    this.memberId = memberId;
  }

  public String getToken() {
    return token;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
