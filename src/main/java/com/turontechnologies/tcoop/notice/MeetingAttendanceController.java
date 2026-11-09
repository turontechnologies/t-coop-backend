package com.turontechnologies.tcoop.notice;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notice.MeetingAttendanceDtos.EntryDto;
import com.turontechnologies.tcoop.notice.MeetingAttendanceDtos.HistoryDto;
import com.turontechnologies.tcoop.notice.MeetingAttendanceDtos.MarkEntry;
import com.turontechnologies.tcoop.notice.MeetingAttendanceDtos.MarkRequest;
import com.turontechnologies.tcoop.notice.MeetingAttendanceDtos.RegisterDto;
import com.turontechnologies.tcoop.notice.MeetingAttendanceDtos.RespondRequest;
import com.turontechnologies.tcoop.notice.MeetingAttendanceDtos.SummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meeting RSVPs and attendance. Every member a Meeting Notice reaches gets a line on its register
 * (MeetingAttendanceService). Members answer until the meeting starts; officers (the co-op admin,
 * or anyone holding a co-op role — the same people who manage notices) see the answers and mark
 * who was Present or Absent. Both sides can see attendance history.
 */
@RestController
public class MeetingAttendanceController {

  /** Meetings are scheduled in the co-op's local time; the platform serves Nigerian co-ops. */
  private static final ZoneId COOP_ZONE = ZoneId.of("Africa/Lagos");

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final NoticeRepository noticeRepository;
  private final MeetingAttendanceRepository attendanceRepository;
  private final MeetingAttendanceService attendanceService;
  private final MemberRepository memberRepository;
  private final AuditLogService auditLogService;
  private final CoopPermissions coopPermissions;

  public MeetingAttendanceController(
      NoticeRepository noticeRepository,
      MeetingAttendanceRepository attendanceRepository,
      MeetingAttendanceService attendanceService,
      MemberRepository memberRepository,
      AuditLogService auditLogService,
      CoopPermissions coopPermissions) {
    this.noticeRepository = noticeRepository;
    this.attendanceRepository = attendanceRepository;
    this.attendanceService = attendanceService;
    this.memberRepository = memberRepository;
    this.auditLogService = auditLogService;
    this.coopPermissions = coopPermissions;
  }

  /** The caller's own line on a meeting — null `entry` when they aren't on its register. */
  @GetMapping("/api/v1/notices/{id}/my-response")
  @Transactional
  public ResponseEntity<?> myResponse(Authentication authentication, @PathVariable UUID id) {
    Member caller = callerOf(authentication);
    if (caller == null) return unauthorized();
    Notice notice = meetingNotice(id);
    if (notice == null || !notice.isSent() || !notice.targetsCoop(String.valueOf(caller.getCooperativeId()))) {
      return notFound();
    }
    attendanceService.ensureRegister(notice);
    MeetingAttendance row = attendanceRepository.findByNoticeIdAndMemberId(id, caller.getId()).orElse(null);
    Map<String, Object> body = new HashMap<>();
    body.put("entry", row == null ? null : EntryDto.from(row, caller.getFullName()));
    body.put("canRespond", row != null && !hasStarted(notice));
    return ResponseEntity.ok(body);
  }

  @PutMapping("/api/v1/notices/{id}/my-response")
  @Transactional
  public ResponseEntity<?> respond(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody RespondRequest request,
      HttpServletRequest httpRequest) {
    Member caller = callerOf(authentication);
    if (caller == null) return unauthorized();
    Notice notice = meetingNotice(id);
    if (notice == null || !notice.isSent() || !notice.targetsCoop(String.valueOf(caller.getCooperativeId()))) {
      return notFound();
    }
    attendanceService.ensureRegister(notice);
    MeetingAttendance row = attendanceRepository.findByNoticeIdAndMemberId(id, caller.getId()).orElse(null);
    if (row == null) {
      return ResponseEntity.status(403).body(Map.of("error", "This meeting notice wasn't addressed to you"));
    }
    if (hasStarted(notice)) {
      return ResponseEntity.status(409).body(Map.of("error", "This meeting has already started, so responses are closed"));
    }
    String reason = request.reason() == null ? null : request.reason().trim();
    if (MeetingAttendance.NOT_ATTENDING.equals(request.response()) && (reason == null || reason.isEmpty())) {
      return ResponseEntity.badRequest().body(Map.of("error", "Tell us why you won't be attending"));
    }

    row.respond(request.response(), reason);
    attendanceRepository.save(row);
    auditLogService.log(
        caller.getId(), caller.getRole(), "Notices", "Update", "RSVP — " + notice.getTitle(), "Success", httpRequest);

    return ResponseEntity.ok(Map.of("entry", EntryDto.from(row, caller.getFullName()), "canRespond", true));
  }

  /** The whole register for a meeting — officers only. */
  @GetMapping("/api/v1/notices/{id}/attendance")
  @Transactional
  public ResponseEntity<?> register(Authentication authentication, @PathVariable UUID id) {
    Member caller = callerOf(authentication);
    if (caller == null) return unauthorized();
    Notice notice = meetingNotice(id);
    if (notice == null || !canViewRegister(caller, notice)) return notFound();

    attendanceService.ensureRegister(notice);
    return ResponseEntity.ok(registerOf(notice, caller));
  }

  /** Officers mark who was Present or Absent. A null `attendance` clears that member's entry. */
  @PutMapping("/api/v1/notices/{id}/attendance")
  @Transactional
  public ResponseEntity<?> mark(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody MarkRequest request,
      HttpServletRequest httpRequest) {
    Member caller = callerOf(authentication);
    if (caller == null) return unauthorized();
    Notice notice = meetingNotice(id);
    if (notice == null || !canViewRegister(caller, notice)) return notFound();
    if (!isOfficerOf(caller, notice)) {
      return ResponseEntity.status(403).body(Map.of("error", "Only the co-operative's officers can take attendance"));
    }
    if (!hasNoticeBoardWrite(caller, notice)) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Your role doesn't have write access to the Notice Board"));
    }
    if (!hasStarted(notice)) {
      return ResponseEntity.status(409).body(Map.of("error", "Attendance opens when the meeting starts"));
    }
    if (hasEnded(notice)) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "Attendance is closed because the meeting duration has elapsed"));
    }

    attendanceService.ensureRegister(notice);
    Map<String, MeetingAttendance> rows =
        attendanceRepository.findAllByNoticeId(id).stream()
            .filter(row -> row.getCooperativeId().equals(caller.getCooperativeId()))
            .collect(Collectors.toMap(MeetingAttendance::getMemberId, Function.identity()));
    for (MarkEntry entry : request.entries()) {
      MeetingAttendance row = rows.get(entry.memberId());
      if (row == null) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", entry.memberId() + " isn't on this meeting's attendance list"));
      }
      String note = entry.note() == null || entry.note().isBlank() ? null : entry.note().trim();
      String attendance = entry.attendance() == null || entry.attendance().isBlank() ? null : entry.attendance();
      boolean unchanged =
          java.util.Objects.equals(attendance, row.getAttendance())
              && java.util.Objects.equals(note, row.getAttendanceNote());
      if (!unchanged) {
        row.mark(attendance, note, caller.getId());
        attendanceRepository.save(row);
      }
    }
    auditLogService.log(
        caller.getId(), caller.getRole(), "Notices", "Update", "Attendance — " + notice.getTitle(), "Success", httpRequest);

    return ResponseEntity.ok(registerOf(notice, caller));
  }

  /** The caller's own attendance history, newest meeting first. */
  @GetMapping("/api/v1/attendance/me")
  public ResponseEntity<?> myHistory(Authentication authentication) {
    Member caller = callerOf(authentication);
    if (caller == null) return unauthorized();
    return ResponseEntity.ok(history(attendanceRepository.findAllByMemberId(caller.getId()), false));
  }

  /** Every member's attendance history in a co-op (optionally one member) — officers only. */
  @GetMapping("/api/v1/cooperatives/{coopId}/attendance")
  public ResponseEntity<?> coopHistory(
      Authentication authentication,
      @PathVariable String coopId,
      @RequestParam(required = false) String memberId) {
    Member caller = callerOf(authentication);
    if (caller == null) return unauthorized();
    boolean allowed =
        "super_admin".equals(caller.getRole())
            || (coopId.equals(caller.getCooperativeId())
                && coopPermissions.isStaff(caller, coopId)
                && coopPermissions.allows(caller, coopId, "Notice Board", null, false));
    if (!allowed) {
      return ResponseEntity.status(403).body(Map.of("error", "Only the co-operative's officers can see everyone's attendance"));
    }
    List<MeetingAttendance> rows =
        memberId == null || memberId.isBlank()
            ? attendanceRepository.findAllByCooperativeId(coopId)
            : attendanceRepository.findAllByMemberId(memberId).stream()
                .filter(row -> coopId.equals(row.getCooperativeId()))
                .toList();
    return ResponseEntity.ok(history(rows, true));
  }

  private RegisterDto registerOf(Notice notice, Member caller) {
    List<MeetingAttendance> rows =
        attendanceRepository.findAllByNoticeId(notice.getId()).stream()
            .filter(row -> "super_admin".equals(caller.getRole()) || row.getCooperativeId().equals(caller.getCooperativeId()))
            .toList();
    Map<String, String> names = namesOf(rows.stream().map(MeetingAttendance::getMemberId).toList());
    List<EntryDto> entries =
        rows.stream()
            .map(row -> EntryDto.from(row, names.getOrDefault(row.getMemberId(), row.getMemberId())))
            .sorted(Comparator.comparing(EntryDto::memberName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    return new RegisterDto(
        notice.getId().toString(),
        notice.getTitle(),
        notice.getMeetingDate() == null ? null : notice.getMeetingDate().toString(),
        notice.getMeetingTime() == null ? null : notice.getMeetingTime().format(HH_MM),
        notice.getMeetingDurationMinutes(),
        attendanceEndsAt(notice),
        canMarkAttendance(notice),
        SummaryDto.of(entries),
        entries);
  }

  private List<HistoryDto> history(List<MeetingAttendance> rows, boolean includeScheduled) {
    Map<UUID, Notice> notices =
        noticeRepository.findAllById(rows.stream().map(MeetingAttendance::getNoticeId).distinct().toList()).stream()
            .collect(Collectors.toMap(Notice::getId, Function.identity()));
    Map<String, String> names = namesOf(rows.stream().map(MeetingAttendance::getMemberId).toList());
    return rows.stream()
        .filter(row -> notices.containsKey(row.getNoticeId()))
        .filter(row -> includeScheduled || notices.get(row.getNoticeId()).isSent())
        .map(
            row -> {
              Notice notice = notices.get(row.getNoticeId());
              return new HistoryDto(
                  notice.getId().toString(),
                  notice.getTitle(),
                  notice.getMeetingDate() == null ? null : notice.getMeetingDate().toString(),
                  notice.getMeetingTime() == null ? null : notice.getMeetingTime().format(HH_MM),
                  row.getMemberId(),
                  names.getOrDefault(row.getMemberId(), row.getMemberId()),
                  row.getResponse(),
                  row.getResponseReason(),
                  row.getAttendance(),
                  row.getAttendanceNote());
            })
        .sorted(
            Comparator.comparing(HistoryDto::meetingDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HistoryDto::meetingTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HistoryDto::memberName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /** Has the meeting started (date + time, or the whole day when no time was set)? */
  private boolean hasStarted(Notice notice) {
    if (notice.getMeetingDate() == null) return false;
    LocalTime start = notice.getMeetingTime() != null ? notice.getMeetingTime() : LocalTime.MAX;
    return !LocalDateTime.now(COOP_ZONE).isBefore(notice.getMeetingDate().atTime(start));
  }

  private boolean hasEnded(Notice notice) {
    if (notice.getMeetingDate() == null) return false;
    LocalTime start = notice.getMeetingTime() != null ? notice.getMeetingTime() : LocalTime.MAX;
    LocalDateTime end =
        notice
            .getMeetingDate()
            .atTime(start)
            .plusMinutes(notice.getMeetingDurationMinutes());
    return !LocalDateTime.now(COOP_ZONE).isBefore(end);
  }

  private boolean canMarkAttendance(Notice notice) {
    return hasStarted(notice) && !hasEnded(notice);
  }

  private String attendanceEndsAt(Notice notice) {
    if (notice.getMeetingDate() == null) return null;
    LocalTime start = notice.getMeetingTime() != null ? notice.getMeetingTime() : LocalTime.MAX;
    return notice
        .getMeetingDate()
        .atTime(start)
        .plusMinutes(notice.getMeetingDurationMinutes())
        .atZone(COOP_ZONE)
        .toInstant()
        .toString();
  }

  private Notice meetingNotice(UUID id) {
    return noticeRepository.findById(id).filter(n -> "Meeting Notice".equals(n.getType())).orElse(null);
  }

  private boolean canViewRegister(Member caller, Notice notice) {
    return "super_admin".equals(caller.getRole())
        || (isOfficerOf(caller, notice) && hasNoticeBoardRead(caller, notice));
  }

  private boolean isOfficerOf(Member caller, Notice notice) {
    String coopId = caller.getCooperativeId();
    return coopId != null
        && notice.targetsCoop(coopId)
        && coopPermissions.isStaff(caller, coopId);
  }

  private boolean hasNoticeBoardRead(Member caller, Notice notice) {
    return "super_admin".equals(caller.getRole())
        || coopPermissions.allows(caller, caller.getCooperativeId(), "Notice Board", null, false);
  }

  private boolean hasNoticeBoardWrite(Member caller, Notice notice) {
    return "super_admin".equals(caller.getRole())
        || coopPermissions.allows(caller, caller.getCooperativeId(), "Notice Board", null, true);
  }

  private Map<String, String> namesOf(List<String> memberIds) {
    return memberRepository.findAllById(memberIds.stream().distinct().toList()).stream()
        .collect(Collectors.toMap(Member::getId, Member::getFullName));
  }

  private Member callerOf(Authentication authentication) {
    return memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
  }

  private static ResponseEntity<?> unauthorized() {
    return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
  }

  private static ResponseEntity<?> notFound() {
    return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that meeting"));
  }
}
