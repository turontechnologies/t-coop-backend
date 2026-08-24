package com.turontechnologies.tcoop.notice;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A real, tenant-isolated Notice Board post. {@code targetCooperativeIds} is never empty — unlike
 * the frontend mock this replaces, there is no "empty means broadcast to everyone" fallback here;
 * every notice explicitly names every co-op it reaches (an admin can only ever name their own, a
 * super admin can name as many as they choose — see {@code NoticeController}).
 */
@Entity
@Table(name = "notices")
public class Notice {

  @Id private UUID id;

  private String type;
  private String title;
  private String message;
  private String recipient;
  private String medium;

  @Column(name = "meeting_date")
  private LocalDate meetingDate;

  @Column(name = "attachment_name")
  private String attachmentName;

  @Column(name = "attachment_url")
  private String attachmentUrl;

  @Column(name = "attachment_size")
  private Long attachmentSize;

  @Column(name = "send_at")
  private LocalDateTime sendAt;

  @Column(name = "created_by_id")
  private String createdById;

  @Column(name = "created_by_name")
  private String createdByName;

  @Column(name = "created_by_role")
  private String createdByRole;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "notice_targets", joinColumns = @JoinColumn(name = "notice_id"))
  @Column(name = "cooperative_id")
  private Set<String> targetCooperativeIds = new HashSet<>();

  protected Notice() {
    // JPA
  }

  public Notice(
      String type,
      String title,
      String message,
      String recipient,
      String medium,
      LocalDate meetingDate,
      LocalDateTime sendAt,
      String createdById,
      String createdByName,
      String createdByRole,
      Set<String> targetCooperativeIds) {
    this.id = UUID.randomUUID();
    this.type = type;
    this.title = title;
    this.message = message;
    this.recipient = recipient;
    this.medium = medium;
    this.meetingDate = meetingDate;
    this.sendAt = sendAt;
    this.createdById = createdById;
    this.createdByName = createdByName;
    this.createdByRole = createdByRole;
    this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    this.targetCooperativeIds = new HashSet<>(targetCooperativeIds);
  }

  public void setAttachment(String name, String url, Long size) {
    this.attachmentName = name;
    this.attachmentUrl = url;
    this.attachmentSize = size;
  }

  public void resend() {
    this.sendAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  public boolean isSent() {
    return !sendAt.isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC));
  }

  /** Whether this notice reaches the given co-op — the sole source of tenant isolation for
   * Notice Board reads. */
  public boolean targetsCoop(String cooperativeId) {
    return targetCooperativeIds.contains(cooperativeId);
  }

  public UUID getId() {
    return id;
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

  public String getRecipient() {
    return recipient;
  }

  public String getMedium() {
    return medium;
  }

  public LocalDate getMeetingDate() {
    return meetingDate;
  }

  public String getAttachmentName() {
    return attachmentName;
  }

  public String getAttachmentUrl() {
    return attachmentUrl;
  }

  public Long getAttachmentSize() {
    return attachmentSize;
  }

  public LocalDateTime getSendAt() {
    return sendAt;
  }

  public String getCreatedById() {
    return createdById;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public String getCreatedByRole() {
    return createdByRole;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public Set<String> getTargetCooperativeIds() {
    return targetCooperativeIds;
  }
}
