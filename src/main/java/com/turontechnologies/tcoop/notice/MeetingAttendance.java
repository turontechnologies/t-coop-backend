package com.turontechnologies.tcoop.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One member's line for one meeting: their RSVP and their attendance. Generated for every member
 * a Meeting Notice reaches (see MeetingAttendanceService). A "not attending" RSVP fills the
 * attendance in automatically (Absent, reason as the note, {@code attendanceAuto}) until an
 * officer marks that member themselves.
 */
@Entity
@Table(name = "meeting_attendance")
public class MeetingAttendance {

  public static final String PHYSICAL = "Physical";
  public static final String ONLINE = "Online";
  public static final String NOT_ATTENDING = "NotAttending";
  /** Present in person. */
  public static final String PRESENT = "Present";
  /** Present, joined online. */
  public static final String PRESENT_ONLINE = "PresentOnline";
  public static final String ABSENT = "Absent";

  @Id private UUID id;

  @Column(name = "notice_id")
  private UUID noticeId;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "member_id")
  private String memberId;

  private String response;

  @Column(name = "response_reason")
  private String responseReason;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  private String attendance;

  @Column(name = "attendance_note")
  private String attendanceNote;

  @Column(name = "attendance_auto")
  private boolean attendanceAuto;

  @Column(name = "marked_by_id")
  private String markedById;

  @Column(name = "marked_at")
  private LocalDateTime markedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  /** The member's own check-in during the meeting: {@link #CHECKIN_IN_PERSON} or
   * {@link #CHECKIN_ONLINE}. It only counts once an officer approves it. */
  @Column(name = "checkin_mode")
  private String checkInMode;

  @Column(name = "checked_in_at")
  private LocalDateTime checkedInAt;

  public static final String CHECKIN_IN_PERSON = "InPerson";
  public static final String CHECKIN_ONLINE = "Online";

  protected MeetingAttendance() {
    // JPA
  }

  public MeetingAttendance(UUID noticeId, String cooperativeId, String memberId) {
    this.id = UUID.randomUUID();
    this.noticeId = noticeId;
    this.cooperativeId = cooperativeId;
    this.memberId = memberId;
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  /** The member's RSVP. "Not attending" marks them Absent with their reason, unless an officer
   * already marked them; switching back to attending clears that automatic entry. */
  public void respond(String response, String reason) {
    this.response = response;
    this.responseReason = NOT_ATTENDING.equals(response) ? reason : null;
    this.respondedAt = LocalDateTime.now(ZoneOffset.UTC);
    boolean officerMarked = attendance != null && !attendanceAuto;
    if (officerMarked) return;
    if (NOT_ATTENDING.equals(response)) {
      this.attendance = ABSENT;
      this.attendanceNote = reason;
      this.attendanceAuto = true;
    } else {
      this.attendance = null;
      this.attendanceNote = null;
      this.attendanceAuto = false;
    }
  }

  /** An officer's entry in the register; {@code attendance} null clears it. */
  public void mark(String attendance, String note, String markedById) {
    this.attendance = attendance;
    this.attendanceNote = note;
    this.attendanceAuto = false;
    this.markedById = attendance == null ? null : markedById;
    this.markedAt = attendance == null ? null : LocalDateTime.now(ZoneOffset.UTC);
  }

  /** Has an officer settled this member's attendance (as opposed to nothing, or the automatic
   * Absent from an apology)? */
  public boolean isReviewed() {
    return attendance != null && !attendanceAuto;
  }

  /** The member says they're here. A member who sent apologies but turned up anyway loses the
   * automatic Absent, so their check-in waits for approval like everyone else's. */
  public void checkIn(String mode) {
    this.checkInMode = mode;
    this.checkedInAt = LocalDateTime.now(ZoneOffset.UTC);
    if (attendanceAuto) {
      this.attendance = null;
      this.attendanceNote = null;
      this.attendanceAuto = false;
    }
  }

  /** Checked in and no officer has decided yet. */
  public boolean isAwaitingApproval() {
    return checkInMode != null && !isReviewed();
  }

  /** The attendance an approved check-in becomes. */
  public String attendanceForCheckIn() {
    return CHECKIN_ONLINE.equals(checkInMode) ? PRESENT_ONLINE : PRESENT;
  }

  public String getCheckInMode() {
    return checkInMode;
  }

  public LocalDateTime getCheckedInAt() {
    return checkedInAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getNoticeId() {
    return noticeId;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getResponse() {
    return response;
  }

  public String getResponseReason() {
    return responseReason;
  }

  public LocalDateTime getRespondedAt() {
    return respondedAt;
  }

  public String getAttendance() {
    return attendance;
  }

  public String getAttendanceNote() {
    return attendanceNote;
  }

  public boolean isAttendanceAuto() {
    return attendanceAuto;
  }

  public String getMarkedById() {
    return markedById;
  }

  public LocalDateTime getMarkedAt() {
    return markedAt;
  }
}
