-- Real platform staff accounts (Settings -> User Management, super admin only) — invited by
-- email, must accept before they can log in. Deliberately reuses the `members` table/login/JWT
-- machinery with a new 'support' role, rather than a second parallel auth system: every existing
-- super-admin-only endpoint already correctly rejects role='support' by default (they're not a
-- co-op admin or super admin), and SubscriptionGateFilter already passes through cleanly for any
-- member with a NULL cooperative_id, so no other authorization path needs touching.

ALTER TABLE members DROP CONSTRAINT CK_members_role;
ALTER TABLE members ADD CONSTRAINT CK_members_role
    CHECK (role IN ('super_admin', 'admin', 'member', 'support'));

ALTER TABLE members DROP CONSTRAINT CK_members_status;
ALTER TABLE members ADD CONSTRAINT CK_members_status
    CHECK (status IN ('Active', 'Inactive', 'Invited'));

CREATE TABLE platform_roles (
    id          UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    name        NVARCHAR(100)    NOT NULL,
    permissions NVARCHAR(500)    NOT NULL, -- comma-separated PERMISSION_MODULES names
    status      NVARCHAR(20)     NOT NULL CONSTRAINT DF_platform_roles_status DEFAULT 'Active',
    created_at  DATETIME2        NOT NULL CONSTRAINT DF_platform_roles_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_platform_roles_name UNIQUE (name),
    CONSTRAINT CK_platform_roles_status CHECK (status IN ('Active', 'Inactive'))
);

ALTER TABLE members ADD
    platform_role_id        UNIQUEIDENTIFIER NULL REFERENCES platform_roles(id),
    invite_token             NVARCHAR(100)    NULL,
    invite_token_expires_at  DATETIME2        NULL,
    invited_at               DATETIME2        NULL,
    accepted_at              DATETIME2        NULL;
GO

-- Separate batch: SQL Server can't reference a column added by ALTER TABLE ... ADD until that
-- batch has compiled, so this index needs its own GO above.
CREATE UNIQUE INDEX UQ_members_invite_token ON members(invite_token) WHERE invite_token IS NOT NULL;

-- No seed data — a super admin invites real platform staff themselves; this table starts empty.
