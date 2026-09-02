package com.turontechnologies.tcoop.support;

import java.time.LocalDateTime;

public record SupportTicketEventDto(
    String id,
    String type,
    String actorId,
    String actorName,
    String actorRole,
    String message,
    String attachmentUrl,
    LocalDateTime createdAt) {

  public static SupportTicketEventDto from(SupportTicketEvent event) {
    return new SupportTicketEventDto(
        event.getId().toString(),
        event.getEventType(),
        event.getActorId(),
        event.getActorName(),
        event.getActorRole(),
        event.getMessage(),
        event.getAttachmentUrl(),
        event.getCreatedAt());
  }
}
