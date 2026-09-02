package com.turontechnologies.tcoop.support;

import java.time.LocalDateTime;
import java.util.List;

public record SupportTicketDto(
    String id,
    String subject,
    String category,
    String description,
    String status,
    String cooperativeId,
    String cooperativeName,
    String raisedById,
    String raisedByName,
    String raisedByRole,
    String assignedToRole,
    LocalDateTime createdAt,
    LocalDateTime resolvedAt,
    String resolutionNote,
    List<SupportTicketEventDto> timeline) {

  public static SupportTicketDto from(
      SupportTicket ticket, String cooperativeName, String raisedByName, List<SupportTicketEventDto> timeline) {
    return new SupportTicketDto(
        ticket.getId().toString(),
        ticket.getSubject(),
        ticket.getCategory(),
        ticket.getDescription(),
        ticket.getStatus(),
        ticket.getCooperativeId(),
        cooperativeName,
        ticket.getRaisedById(),
        raisedByName,
        ticket.getRaisedByRole(),
        ticket.getAssignedToRole(),
        ticket.getCreatedAt(),
        ticket.getResolvedAt(),
        ticket.getResolutionNote(),
        timeline);
  }
}
