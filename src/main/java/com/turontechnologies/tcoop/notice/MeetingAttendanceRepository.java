package com.turontechnologies.tcoop.notice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingAttendanceRepository extends JpaRepository<MeetingAttendance, UUID> {

  List<MeetingAttendance> findAllByNoticeId(UUID noticeId);

  Optional<MeetingAttendance> findByNoticeIdAndMemberId(UUID noticeId, String memberId);

  List<MeetingAttendance> findAllByMemberId(String memberId);

  List<MeetingAttendance> findAllByCooperativeId(String cooperativeId);

  boolean existsByNoticeId(UUID noticeId);
}
