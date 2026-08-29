-- Adds the notification type CooperativeController.transferAdmin sends to every super admin when
-- a co-op hands its admin role over to someone new.
ALTER TABLE notifications DROP CONSTRAINT CK_notifications_type;

ALTER TABLE notifications ADD CONSTRAINT CK_notifications_type CHECK (type IN (
    'SUBSCRIPTION_EXPIRING',
    'SUBSCRIPTION_EXPIRED',
    'SUBSCRIPTION_RENEWED',
    'NOTICE_BOARD',
    'COOPERATIVE_WELCOME',
    'COOPERATIVE_STATUS',
    'COOPERATIVE_ADMIN_TRANSFERRED',
    'MEMBER_ADDED',
    'MEMBER_STATUS',
    'PLATFORM_STAFF_JOINED',
    'COOP_ROLE_ASSIGNED',
    'COOP_ROLE_REMOVED'
));
