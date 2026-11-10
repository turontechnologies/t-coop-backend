-- Attendance distinguishes how someone was present: in person ('Present') or online
-- ('PresentOnline'), alongside 'Absent'.
ALTER TABLE meeting_attendance DROP CONSTRAINT CK_meeting_attendance_attendance;
ALTER TABLE meeting_attendance ADD CONSTRAINT CK_meeting_attendance_attendance
    CHECK (attendance IN ('Present', 'PresentOnline', 'Absent'));
