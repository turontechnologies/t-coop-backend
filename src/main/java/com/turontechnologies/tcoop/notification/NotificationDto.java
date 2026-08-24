package com.turontechnologies.tcoop.notification;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record NotificationDto(
    Long id, String type, String title, String message, String link, boolean read, String createdAt) {

  public static NotificationDto from(Notification notification) {
    return new NotificationDto(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getMessage(),
        notification.getLink(),
        notification.isRead(),
        notification.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
  }
}
