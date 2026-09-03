-- Expo push tokens for the mobile app — one row per device that's ever registered, addressed to
-- whichever member most recently logged in on it. A token is globally unique to a device
-- install, not to a member: re-registering the same token under a different member (e.g. someone
-- logs out and a different person logs in on the same phone) reassigns ownership rather than
-- creating a duplicate row, so a stale login never keeps receiving another person's pushes.
CREATE TABLE push_tokens (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    member_id NVARCHAR(20) NOT NULL REFERENCES members(id),
    token NVARCHAR(200) NOT NULL,
    platform NVARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL CONSTRAINT DF_push_tokens_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_push_tokens_token UNIQUE (token)
);

CREATE INDEX IX_push_tokens_member ON push_tokens(member_id);
