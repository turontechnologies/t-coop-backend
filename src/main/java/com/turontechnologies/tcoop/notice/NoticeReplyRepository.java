package com.turontechnologies.tcoop.notice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeReplyRepository extends JpaRepository<NoticeReply, UUID> {

  List<NoticeReply> findAllByNoticeIdOrderByCreatedAtAsc(UUID noticeId);
}
