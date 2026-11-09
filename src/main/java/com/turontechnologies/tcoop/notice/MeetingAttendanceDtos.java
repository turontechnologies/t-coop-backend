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
      String markedAt) {
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
          instant(row.getMarkedAt()));
    }
  }

  public record SummaryDto(
      int invited,
      int physical,
      int online,
      int notAttending,
      int noResponse,
      int present,
      int absent,
      int unmarked) {
    static SummaryDto of(List<EntryDto> entries) {
      int physical = 0, online = 0, notAttending = 0, present = 0, absent = 0;
      for (EntryDto e : entries) {
        if (MeetingAttendance.PHYSICAL.equals(e.response())) physical++;
        else if (MeetingAttendance.ONLINE.equals(e.response())) online++;
        else if (MeetingAttendance.NOT_ATTENDING.equals(e.response())) notAttending++;
        if (MeetingAttendance.PRESENT.equals(e.attendance())) present++;
        else if (MeetingAttendance.ABSENT.equals(e.attendance())) absent++;
      }
      int invited = entries.size();
      return new SummaryDto(
          invited,
          physical,
          online,
          notAttending,
          invited - physical - online - notAttending,
          present,
          absent,
          invited - present - absent);
    }
  }

  /** A meeting's whole register, for officers. */
  public record RegisterDto(
      String noticeId,
      String title,
      String meetingDate,
      String meetingTime,
      SummaryDto summary,
      List<EntryDto> entries) {}

  /** One line of someone's attendance history. */
  public record HistoryDto(
      String noticeId,
      String title,
      String meetingDate,
      String meetingTime,
      String memberId,
      String memberName,
      String response,
      String responseReason,
      String attendance,
      String attendanceNote) {}

  public record RespondRequest(
      @NotBlank
          @Pattern(regexp = "Physical|Online|NotAttending", message = "Choose how you'll attend")
          String response,
      @Size(max = 500, message = "Keep the reason under 500 characters") String reason) {}

  public record MarkRequest(@NotNull @Valid List<MarkEntry> entries) {}

  public record MarkEntry(
      @NotBlank String memberId,
      @Pattern(regexp = "Present|Absent", message = "Attendance must be Present or Absent")
          String attendance,
      @Size(max = 500, message = "Keep the note under 500 characters") String note) {}
}
