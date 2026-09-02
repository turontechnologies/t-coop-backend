package com.turontechnologies.tcoop.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** A member's issue raised to their own co-op's admin, or an admin's own issue raised straight to
 * the super admin. {@link #assignedToRole} tracks who currently owns resolving it — it only ever
 * moves forward (admin to super_admin on escalation), never back. */
@Entity
@Table(name = "support_tickets")
public class SupportTicket {

  @Id private UUID id;

  private String subject;
  private String category;
  private String description;
  private String status;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "raised_by_id")
  private String raisedById;

  @Column(name = "raised_by_role")
  private String raisedByRole;

  @Column(name = "assigned_to_role")
  private String assignedToRole;

  @Column(name = "resolution_note")
  private String resolutionNote;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  protected SupportTicket() {
    // JPA
  }

  public SupportTicket(
      String subject,
      String category,
      String description,
      String cooperativeId,
      String raisedById,
      String raisedByRole,
      String assignedToRole) {
    this.id = UUID.randomUUID();
    this.subject = subject;
    this.category = category;
    this.description = description;
    this.status = "Open";
    this.cooperativeId = cooperativeId;
    this.raisedById = raisedById;
    this.raisedByRole = raisedByRole;
    this.assignedToRole = assignedToRole;
    this.createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public String getSubject() {
    return subject;
  }

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getRaisedById() {
    return raisedById;
  }

  public String getRaisedByRole() {
    return raisedByRole;
  }

  public String getAssignedToRole() {
    return assignedToRole;
  }

  public void setAssignedToRole(String assignedToRole) {
    this.assignedToRole = assignedToRole;
  }

  public String getResolutionNote() {
    return resolutionNote;
  }

  public void setResolutionNote(String resolutionNote) {
    this.resolutionNote = resolutionNote;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(LocalDateTime resolvedAt) {
    this.resolvedAt = resolvedAt;
  }
}
