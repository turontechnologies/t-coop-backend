package com.turontechnologies.tcoop.notice;

import com.turontechnologies.tcoop.member.Member;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record NoticeReplyDto(
    String id,
    String noticeId,
    String authorId,
    String authorName,
    String authorRole,
    String authorAvatarUrl,
    String message,
    String createdAt) {

  public static NoticeReplyDto from(NoticeReply reply, Member author) {
    return new NoticeReplyDto(
        reply.getId().toString(),
        reply.getNoticeId().toString(),
        reply.getAuthorId(),
        author != null ? author.getFullName() : reply.getAuthorId(),
        author != null ? author.getRole() : "member",
        author != null ? author.getAvatarUrl() : null,
        reply.getMessage(),
        reply.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
  }
}
