-- Notice Board, for real. Until now this entire feature lived only in the frontend's browser
-- localStorage (a Zustand store with same-browser "storage"-event cross-tab sync) -- it never
-- reached a second user or device, so "the right people get the right notification" was
-- structurally impossible. This gives it a real, tenant-isolated home.
--
-- targetCoopIds moves from an optional frontend field (empty = broadcast-to-everyone, a
-- back-compat quirk from before per-co-op targeting existed) to a required junction table here --
-- every notice created against this schema must explicitly name which co-op(s) it reaches. An
-- admin can only ever target their own co-op (enforced in NoticeController, not just the client);
-- only a super admin can name more than one.
CREATE TABLE notices (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    type NVARCHAR(30) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    message NVARCHAR(MAX) NOT NULL,
    recipient NVARCHAR(30) NOT NULL,
    medium NVARCHAR(20) NOT NULL,
    -- Only meaningful when type = 'Meeting Notice'.
    meeting_date DATE NULL,
    attachment_name NVARCHAR(255) NULL,
    attachment_url NVARCHAR(500) NULL,
    attachment_size BIGINT NULL,
    -- A future value means "Scheduled"; once it's passed, the notice is "Sent". Resending simply
    -- rewrites this to now.
    send_at DATETIME2 NOT NULL,
    created_by_id NVARCHAR(20) NOT NULL REFERENCES members(id),
    -- Snapshotted at creation time deliberately -- a notice's "From" line shouldn't silently
    -- change if the sender later edits their profile name.
    created_by_name NVARCHAR(200) NOT NULL,
    created_by_role NVARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL CONSTRAINT DF_notices_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_notices_type CHECK (type IN ('General', 'Meeting Notice', 'Meeting Minutes')),
    CONSTRAINT CK_notices_recipient CHECK (recipient IN ('All Members', 'All Admins', 'All Members & Admins')),
    CONSTRAINT CK_notices_medium CHECK (medium IN ('Email', 'SMS', 'Email & SMS'))
);

CREATE INDEX IX_notices_send_at ON notices(send_at DESC);

CREATE TABLE notice_targets (
    notice_id UNIQUEIDENTIFIER NOT NULL REFERENCES notices(id) ON DELETE CASCADE,
    cooperative_id NVARCHAR(20) NOT NULL REFERENCES cooperatives(id),
    CONSTRAINT PK_notice_targets PRIMARY KEY (notice_id, cooperative_id)
);

CREATE INDEX IX_notice_targets_cooperative_id ON notice_targets(cooperative_id);

CREATE TABLE notice_replies (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    notice_id UNIQUEIDENTIFIER NOT NULL REFERENCES notices(id) ON DELETE CASCADE,
    author_id NVARCHAR(20) NOT NULL REFERENCES members(id),
    message NVARCHAR(MAX) NOT NULL,
    created_at DATETIME2 NOT NULL CONSTRAINT DF_notice_replies_created_at DEFAULT SYSUTCDATETIME()
);

CREATE INDEX IX_notice_replies_notice_id ON notice_replies(notice_id);
