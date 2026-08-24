-- A generic, per-recipient notification feed. Every row is addressed to exactly one member —
-- there is no "broadcast" row; a notification that reaches many people (e.g. every member of a
-- co-op, or every super admin) is fanned out into one row per recipient at creation time. This is
-- deliberate: it keeps read/unread state trivially correct per person (no shared read-markers
-- table to reconcile) and makes the tenant-isolation rule structural rather than a query-time
-- filter someone could get wrong later — a member of co-op A can never see a row addressed to
-- co-op B's admin, because no such row exists for them.
--
-- related_cooperative_id + related_expires_at exist purely so the subscription-expiry reminder
-- job (see SubscriptionExpiryReminderJob) can check "have I already warned this co-op about this
-- exact expiry date" without re-sending the same warning every day it runs.
CREATE TABLE notifications (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    recipient_member_id NVARCHAR(20) NOT NULL REFERENCES members(id),
    type NVARCHAR(40) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    message NVARCHAR(1000) NOT NULL,
    -- Relative frontend path to navigate to when the notification is clicked; NULL if it's
    -- informational only.
    link NVARCHAR(300) NULL,
    related_cooperative_id NVARCHAR(20) NULL REFERENCES cooperatives(id),
    related_expires_at DATE NULL,
    is_read BIT NOT NULL CONSTRAINT DF_notifications_is_read DEFAULT 0,
    created_at DATETIME2 NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_notifications_type CHECK (type IN (
        'SUBSCRIPTION_EXPIRING',
        'SUBSCRIPTION_EXPIRED',
        'SUBSCRIPTION_RENEWED',
        'NOTICE_BOARD',
        'COOPERATIVE_WELCOME',
        'COOPERATIVE_STATUS',
        'MEMBER_ADDED',
        'MEMBER_STATUS',
        'PLATFORM_STAFF_JOINED'
    ))
);

CREATE INDEX IX_notifications_recipient_created ON notifications(recipient_member_id, created_at DESC);
CREATE INDEX IX_notifications_recipient_unread ON notifications(recipient_member_id, is_read);

-- Dedup lookup for the daily expiry-reminder job: "has this exact (co-op, expiry date, type)
-- already been warned about". A renewal changes subscription_expires_at, which naturally
-- invalidates any prior warning's relevance without needing to delete/expire old rows.
CREATE INDEX IX_notifications_expiry_dedup
    ON notifications(recipient_member_id, type, related_cooperative_id, related_expires_at);
