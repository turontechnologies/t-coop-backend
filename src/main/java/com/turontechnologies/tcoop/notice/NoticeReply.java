package com.turontechnologies.tcoop.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notice_replies")
public class NoticeReply {

  @Id private UUID id;

  @Column(name = "notice_id")
  private UUID noticeId;

  @Column(name = "author_id")
  private String authorId;

  private String message;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected NoticeReply() {
    // JPA
  }

  public NoticeReply(UUID noticeId, String authorId, String message) {
    this.id = UUID.randomUUID();
    this.noticeId = noticeId;
    this.authorId = authorId;
    this.message = message;
    this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  public UUID getId() {
    return id;
  }

  public UUID getNoticeId() {
    return noticeId;
  }

  public String getAuthorId() {
    return authorId;
  }

  public String getMessage() {
    return message;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
