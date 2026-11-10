-- 'PresentOnline' (V45) is longer than the original NVARCHAR(10) attendance column.
ALTER TABLE meeting_attendance DROP CONSTRAINT CK_meeting_attendance_attendance;
ALTER TABLE meeting_attendance ALTER COLUMN attendance NVARCHAR(20) NULL;
ALTER TABLE meeting_attendance ADD CONSTRAINT CK_meeting_attendance_attendance
    CHECK (attendance IN ('Present', 'PresentOnline', 'Absent'));
