-- Adds the two notification types CoopUserController sends when an admin assigns/revokes a
-- co-op-scoped staff role. MSSQL requires dropping and re-adding a named CHECK constraint to
-- widen it.
ALTER TABLE notifications DROP CONSTRAINT CK_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT CK_notifications_type CHECK (type IN (
    'SUBSCRIPTION_EXPIRING',
    'SUBSCRIPTION_EXPIRED',
    'SUBSCRIPTION_RENEWED',
    'NOTICE_BOARD',
    'COOPERATIVE_WELCOME',
    'COOPERATIVE_STATUS',
    'MEMBER_ADDED',
    'MEMBER_STATUS',
    'PLATFORM_STAFF_JOINED',
    'COOP_ROLE_ASSIGNED',
    'COOP_ROLE_REMOVED'
));
