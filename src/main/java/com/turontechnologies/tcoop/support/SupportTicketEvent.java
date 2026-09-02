package com.turontechnologies.tcoop.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** One entry in a ticket's timeline — raised, replied to, escalated, or resolved. This IS the
 * ticket's audit trail; nothing else logs these transitions separately. */
@Entity
@Table(name = "support_ticket_events")
public class SupportTicketEvent {

  @Id private UUID id;

  @Column(name = "ticket_id")
  private UUID ticketId;

  @Column(name = "event_type")
  private String eventType;

  @Column(name = "actor_id")
  private String actorId;

  @Column(name = "actor_name")
  private String actorName;

  @Column(name = "actor_role")
  private String actorRole;

  private String message;

  @Column(name = "attachment_url")
  private String attachmentUrl;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected SupportTicketEvent() {
    // JPA
  }

  public SupportTicketEvent(
      UUID ticketId,
      String eventType,
      String actorId,
      String actorName,
      String actorRole,
      String message,
      String attachmentUrl) {
    this.id = UUID.randomUUID();
    this.ticketId = ticketId;
    this.eventType = eventType;
    this.actorId = actorId;
    this.actorName = actorName;
    this.actorRole = actorRole;
    this.message = message;
    this.attachmentUrl = attachmentUrl;
    this.createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTicketId() {
    return ticketId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getActorId() {
    return actorId;
  }

  public String getActorName() {
    return actorName;
  }

  public String getActorRole() {
    return actorRole;
  }

  public String getMessage() {
    return message;
  }

  public String getAttachmentUrl() {
    return attachmentUrl;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
