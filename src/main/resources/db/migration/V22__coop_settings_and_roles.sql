-- Closes out the admin Settings page's remaining mock tabs in one pass:
-- 1. A co-op's own receiving bank account -- distinct from its admin's *personal* payout account
--    (members.bank_code/account_number/account_name), which already exists and is unrelated.
-- 2. Co-op-scoped custom roles + invited users -- the same real invite-by-email/accept/set-
--    password machinery already built for platform staff (see V18__platform_staff.sql), scoped to
--    one co-operative instead of the whole platform. An admin manages these entirely within their
--    own co-op; a super admin can too (oversight), never a plain member.
ALTER TABLE cooperatives ADD
    bank_code       NVARCHAR(20)   NULL,
    account_number  NVARCHAR(20)   NULL,
    account_name    NVARCHAR(200)  NULL;

CREATE TABLE coop_roles (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    name            NVARCHAR(100)    NOT NULL,
    -- Comma-separated PERMISSION_MODULES names, same convention as platform_roles.permissions —
    -- a handful of fixed strings, a real join table would be over-engineering.
    permissions     NVARCHAR(500)    NULL,
    status          NVARCHAR(20)     NOT NULL CONSTRAINT DF_coop_roles_status DEFAULT 'Active',
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_coop_roles_created_at DEFAULT SYSUTCDATETIME()
);

CREATE INDEX IX_coop_roles_cooperative_id ON coop_roles(cooperative_id);
-- A role name only needs to be unique within its own co-op, not platform-wide.
CREATE UNIQUE INDEX UQ_coop_roles_cooperative_name ON coop_roles(cooperative_id, name);

-- A member invited under a co-op-scoped role -- role stays 'member' (they're still fundamentally
-- a member of that co-op), this just grants them a named, granular permission set on top,
-- mirroring how platform_role_id works for role 'support'. NULL for every ordinary member/admin.
ALTER TABLE members ADD coop_role_id UNIQUEIDENTIFIER NULL REFERENCES coop_roles(id);

CREATE INDEX IX_members_coop_role_id ON members(coop_role_id);
