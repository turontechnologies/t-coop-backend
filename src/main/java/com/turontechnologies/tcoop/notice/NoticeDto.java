package com.turontechnologies.tcoop.notice;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record NoticeDto(
    String id,
    String type,
    String title,
    String message,
    String recipient,
    String medium,
    String meetingDate,
    NoticeAttachmentDto attachment,
    String sendAt,
    String status,
    String createdByName,
    String createdByRole,
    String createdAt,
    List<String> targetCoopIds) {

  public static NoticeDto from(Notice notice) {
    NoticeAttachmentDto attachment =
        notice.getAttachmentUrl() == null
            ? null
            : new NoticeAttachmentDto(
                notice.getAttachmentName(), notice.getAttachmentUrl(), notice.getAttachmentSize());

    return new NoticeDto(
        notice.getId().toString(),
        notice.getType(),
        notice.getTitle(),
        notice.getMessage(),
        notice.getRecipient(),
        notice.getMedium(),
        notice.getMeetingDate() == null ? null : notice.getMeetingDate().toString(),
        attachment,
        notice.getSendAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
        notice.isSent() ? "Sent" : "Scheduled",
        notice.getCreatedByName(),
        notice.getCreatedByRole(),
        notice.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
        notice.getTargetCooperativeIds().stream().sorted().toList());
  }

  public record NoticeAttachmentDto(String name, String url, Long size) {}
}
