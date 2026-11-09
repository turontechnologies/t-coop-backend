-- Meetings are open for attendance marking from their start until their scheduled duration ends.
-- Minutes can be linked to the meeting they record so they can be distributed to its audience.

ALTER TABLE notices ADD
    meeting_duration_minutes INT NOT NULL
        CONSTRAINT DF_notices_meeting_duration_minutes DEFAULT 120,
    meeting_notice_id UNIQUEIDENTIFIER NULL
        REFERENCES notices(id) ON DELETE SET NULL;
GO

ALTER TABLE notices ADD CONSTRAINT CK_notices_meeting_duration_minutes
    CHECK (meeting_duration_minutes BETWEEN 1 AND 1440);

CREATE INDEX IX_notices_meeting_notice_id ON notices(meeting_notice_id);

-- Officer roles need write access to the meeting register and minutes, in addition to
-- their existing read access to the notice board.
UPDATE coop_roles
SET permissions = REPLACE(permissions, 'Notice Board::read', 'Notice Board::write')
WHERE office IS NOT NULL
  AND permissions LIKE '%Notice Board::read%';
