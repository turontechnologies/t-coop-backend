-- Members check themselves in during a meeting (in person or online); an officer then approves
-- the check-in, which is what turns it into real attendance. Plus an optional venue / link on
-- meeting notices.
ALTER TABLE meeting_attendance ADD
    checkin_mode NVARCHAR(20) NULL,
    checked_in_at DATETIME2 NULL;
GO

ALTER TABLE meeting_attendance ADD CONSTRAINT CK_meeting_attendance_checkin_mode
    CHECK (checkin_mode IS NULL OR checkin_mode IN ('InPerson', 'Online'));
GO

ALTER TABLE notices ADD meeting_venue NVARCHAR(200) NULL;
GO
