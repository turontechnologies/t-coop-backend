-- Meeting notices get a time, an RSVP from each member, and an attendance register.
--
-- When a co-op sends a Meeting Notice, one meeting_attendance row is generated per member it
-- reaches. A member answers it (attending in person / online / not attending + reason); an
-- officer then marks who was Present or Absent. A "not attending" answer fills the register in
-- automatically (Absent, with the reason as the note — attendance_auto = 1) until an officer
-- marks that member themselves.

ALTER TABLE notices ADD meeting_time TIME(0) NULL;
GO

CREATE TABLE meeting_attendance (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    notice_id UNIQUEIDENTIFIER NOT NULL REFERENCES notices(id) ON DELETE CASCADE,
    cooperative_id NVARCHAR(20) NOT NULL REFERENCES cooperatives(id),
    member_id NVARCHAR(20) NOT NULL REFERENCES members(id),
    -- The member's answer: null until they respond.
    response NVARCHAR(20) NULL,
    response_reason NVARCHAR(500) NULL,
    responded_at DATETIME2 NULL,
    -- The register: null until marked (or filled in by a "not attending" answer).
    attendance NVARCHAR(10) NULL,
    attendance_note NVARCHAR(500) NULL,
    attendance_auto BIT NOT NULL CONSTRAINT DF_meeting_attendance_auto DEFAULT 0,
    marked_by_id NVARCHAR(20) NULL REFERENCES members(id),
    marked_at DATETIME2 NULL,
    created_at DATETIME2 NOT NULL CONSTRAINT DF_meeting_attendance_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_meeting_attendance_notice_member UNIQUE (notice_id, member_id),
    CONSTRAINT CK_meeting_attendance_response CHECK (response IN ('Physical', 'Online', 'NotAttending')),
    CONSTRAINT CK_meeting_attendance_attendance CHECK (attendance IN ('Present', 'Absent'))
);

CREATE INDEX IX_meeting_attendance_member ON meeting_attendance(member_id);
CREATE INDEX IX_meeting_attendance_cooperative ON meeting_attendance(cooperative_id);
