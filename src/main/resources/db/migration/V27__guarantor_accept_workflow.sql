-- Each guarantor named when a member is added now has to actually accept before they count as a
-- real guarantor — a proper row per guarantor instead of a name inside a comma-separated string,
-- so it can carry a real accept/decline status and an unguessable accept token (mirrors the
-- platform-staff/coop-role invite-accept pattern already used elsewhere in this codebase).
CREATE TABLE member_guarantors (
    id                     UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    member_id              NVARCHAR(20)     NOT NULL REFERENCES members(id),
    cooperative_id         NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    name                   NVARCHAR(200)    NOT NULL,
    email                  NVARCHAR(255)    NOT NULL,
    status                 NVARCHAR(20)     NOT NULL CONSTRAINT DF_member_guarantors_status DEFAULT 'Pending',
    accept_token           NVARCHAR(255)    NULL,
    accept_token_expires_at DATETIME2       NULL,
    responded_at           DATETIME2        NULL,
    created_at             DATETIME2        NOT NULL CONSTRAINT DF_member_guarantors_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_member_guarantors_status CHECK (status IN ('Pending', 'Accepted', 'Declined'))
);

CREATE INDEX IX_member_guarantors_member_id ON member_guarantors(member_id);
CREATE UNIQUE INDEX UQ_member_guarantors_accept_token ON member_guarantors(accept_token) WHERE accept_token IS NOT NULL;
