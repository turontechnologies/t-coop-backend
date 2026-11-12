package com.turontechnologies.tcoop.notice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Request/response shapes for meeting RSVPs and attendance (MeetingAttendanceController). */
public final class MeetingAttendanceDtos {

  private MeetingAttendanceDtos() {}

  private static String instant(java.time.LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
  }

  /** One member's line on a meeting's register. */
  public record EntryDto(
      String memberId,
      String memberName,
      String response,
      String responseReason,
      String respondedAt,
      String attendance,
      String attendanceNote,
      boolean attendanceAuto,
      String markedAt,
      /** "InPerson" | "Online" — the member's own check-in, null when they haven't. */
      String checkInMode,
      String checkedInAt,
      /** Checked in, and no officer has approved or rejected it yet. */
      boolean awaitingApproval) {
    static EntryDto from(MeetingAttendance row, String memberName) {
      return new EntryDto(
          row.getMemberId(),
          memberName,
          row.getResponse(),
          row.getResponseReason(),
          instant(row.getRespondedAt()),
          row.getAttendance(),
          row.getAttendanceNote(),
          row.isAttendanceAuto(),
          instant(row.getMarkedAt()),
          row.getCheckInMode(),
          instant(row.getCheckedInAt()),
          row.isAwaitingApproval());
    }
  }

  public record SummaryDto(
      int invited,
      int physical,
      int online,
      int notAttending,
      int noResponse,
      /** Everyone present, in person or online. */
      int present,
      int presentInPerson,
      int presentOnline,
      int absent,
      int unmarked,
      int checkedIn,
      int awaitingApproval) {
    static SummaryDto of(List<EntryDto> entries) {
      int physical = 0, online = 0, notAttending = 0, inPerson = 0, presentOnline = 0, absent = 0;
      int checkedIn = 0, awaiting = 0;
      for (EntryDto e : entries) {
        if (e.checkInMode() != null) checkedIn++;
        if (e.awaitingApproval()) awaiting++;
        if (MeetingAttendance.PHYSICAL.equals(e.response())) physical++;
        else if (MeetingAttendance.ONLINE.equals(e.response())) online++;
        else if (MeetingAttendance.NOT_ATTENDING.equals(e.response())) notAttending++;
        if (MeetingAttendance.PRESENT.equals(e.attendance())) inPerson++;
        else if (MeetingAttendance.PRESENT_ONLINE.equals(e.attendance())) presentOnline++;
        else if (MeetingAttendance.ABSENT.equals(e.attendance())) absent++;
      }
      int invited = entries.size();
      return new SummaryDto(
          invited,
          physical,
          online,
          notAttending,
          invited - physical - online - notAttending,
          inPerson + presentOnline,
          inPerson,
          presentOnline,
          absent,
          invited - inPerson - presentOnline - absent,
          checkedIn,
          awaiting);
    }
  }

  /**
   * When a meeting's attendance windows open and close (ISO instants). Officers mark and approve
   * from {@code startsAt} to {@code endsAt}; members check themselves in from {@code startsAt} to
   * {@code checkInClosesAt}, 30 minutes before the end.
   */
  public record WindowDto(String startsAt, String checkInClosesAt, String endsAt) {}

  /** A meeting's whole register, for officers. */
  public record RegisterDto(
      String noticeId,
      String title,
      String meetingDate,
      String meetingTime,
      int meetingDurationMinutes,
      String meetingVenue,
      String attendanceEndsAt,
      boolean canMarkAttendance,
      WindowDto window,
      SummaryDto summary,
      List<EntryDto> entries) {}

  /** The caller's own line on a meeting, plus what they can do right now. */
  public record MyResponseDto(
      EntryDto entry,
      boolean canRespond,
      boolean canCheckIn,
      WindowDto window) {}

  /** One line of someone's attendance history. */
  public record HistoryDto(
      String noticeId,
      String title,
      String meetingDate,
      String meetingTime,
      int meetingDurationMinutes,
      String meetingVenue,
      /** The meeting has started — upcoming meetings don't count towards attendance rates. */
      boolean held,
      String memberId,
      String memberName,
      String response,
      String responseReason,
      String attendance,
      String attendanceNote,
      String checkInMode,
      boolean awaitingApproval) {}

  public record CheckInRequest(
      @NotBlank @Pattern(regexp = "InPerson|Online", message = "Choose in person or online")
          String mode) {}

  /** Approve these members' check-ins (each becomes Present in person or online). */
  public record ApproveRequest(@NotNull @Size(min = 1, message = "Pick at least one member") List<String> memberIds) {}

  public record RespondRequest(
      @NotBlank
          @Pattern(regexp = "Physical|Online|NotAttending", message = "Choose how you'll attend")
          String response,
      @Size(max = 500, message = "Keep the reason under 500 characters") String reason) {}

  public record MarkRequest(@NotNull @Valid List<MarkEntry> entries) {}

  public record MarkEntry(
      @NotBlank String memberId,
      @Pattern(
              regexp = "Present|PresentOnline|Absent",
              message = "Attendance must be Present (in person), Present online, or Absent")
          String attendance,
      @Size(max = 500, message = "Keep the note under 500 characters") String note) {}
}
