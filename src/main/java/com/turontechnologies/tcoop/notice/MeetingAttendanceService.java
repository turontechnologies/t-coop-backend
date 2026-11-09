package com.turontechnologies.tcoop.notice;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a Meeting Notice's attendance register: one line per active member it reaches. */
@Service
public class MeetingAttendanceService {

  private final MeetingAttendanceRepository attendanceRepository;
  private final MemberRepository memberRepository;

  public MeetingAttendanceService(
      MeetingAttendanceRepository attendanceRepository, MemberRepository memberRepository) {
    this.attendanceRepository = attendanceRepository;
    this.memberRepository = memberRepository;
  }

  /** Adds anyone the notice reaches who isn't on its register yet. Safe to call repeatedly — it's
   * also how meeting notices sent before registers existed get one on first view. */
  @Transactional
  public void ensureRegister(Notice notice) {
    if (!"Meeting Notice".equals(notice.getType())) return;
    Set<String> existing =
        attendanceRepository.findAllByNoticeId(notice.getId()).stream()
            .map(MeetingAttendance::getMemberId)
            .collect(Collectors.toSet());
    for (String coopId : notice.getTargetCooperativeIds()) {
      for (Member member : recipients(coopId, notice.getRecipient())) {
        if (existing.add(member.getId())) {
          attendanceRepository.save(new MeetingAttendance(notice.getId(), coopId, member.getId()));
        }
      }
    }
  }

  /** Same audience rule as the notice's own delivery ("All Admins" is the co-op's admin, "All
   * Members" excludes them, "All Members & Admins" is everyone) — active members only. */
  private List<Member> recipients(String cooperativeId, String recipient) {
    List<Member> coopMembers =
        memberRepository.findAllByCooperativeId(cooperativeId).stream()
            .filter(member -> "Active".equals(member.getStatus()))
            .toList();
    return switch (recipient) {
      case "All Admins" -> coopMembers.stream().filter(m -> m.getId().equals(cooperativeId)).toList();
      case "All Members" -> coopMembers.stream().filter(m -> "member".equals(m.getRole())).toList();
      default -> coopMembers;
    };
  }
}
