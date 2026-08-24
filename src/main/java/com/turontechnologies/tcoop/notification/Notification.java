package com.turontechnologies.tcoop.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "recipient_member_id")
  private String recipientMemberId;

  private String type;
  private String title;
  private String message;
  private String link;

  @Column(name = "related_cooperative_id")
  private String relatedCooperativeId;

  @Column(name = "related_expires_at")
  private LocalDate relatedExpiresAt;

  @Column(name = "is_read")
  private boolean read;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected Notification() {
    // JPA
  }

  public Notification(
      String recipientMemberId,
      String type,
      String title,
      String message,
      String link,
      String relatedCooperativeId,
      LocalDate relatedExpiresAt) {
    this.recipientMemberId = recipientMemberId;
    this.type = type;
    this.title = title;
    this.message = message;
    this.link = link;
    this.relatedCooperativeId = relatedCooperativeId;
    this.relatedExpiresAt = relatedExpiresAt;
    this.read = false;
    this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  public Long getId() {
    return id;
  }

  public String getRecipientMemberId() {
    return recipientMemberId;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getMessage() {
    return message;
  }

  public String getLink() {
    return link;
  }

  public String getRelatedCooperativeId() {
    return relatedCooperativeId;
  }

  public LocalDate getRelatedExpiresAt() {
    return relatedExpiresAt;
  }

  public boolean isRead() {
    return read;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void markRead() {
    this.read = true;
  }
}
