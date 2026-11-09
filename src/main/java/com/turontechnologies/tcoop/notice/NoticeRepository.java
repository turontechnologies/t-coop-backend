package com.turontechnologies.tcoop.notice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

  /** Minutes linked to a meeting notice. */
  java.util.List<Notice> findAllByMeetingNoticeId(UUID meetingNoticeId);

  List<Notice> findAllByOrderBySendAtDesc(Pageable pageable);
}
