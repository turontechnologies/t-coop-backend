-- Adds the notification types the new loan self-service flow sends: to the named guarantor when
-- a member applies, to the applicant when the guarantor rejects, and to the co-op admin when the
-- guarantor accepts (so it's ready for the admin's own decision).
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
    'COOP_ROLE_REMOVED',
    'LOAN_GUARANTOR_REQUEST',
    'LOAN_GUARANTOR_ACCEPTED',
    'LOAN_GUARANTOR_REJECTED',
    'LOAN_DECISION'
));
