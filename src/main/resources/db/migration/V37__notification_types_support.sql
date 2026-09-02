-- Adds the notification types the new real Support ticket flow sends: to the assignee when a
-- ticket is raised or escalated to them, and to the raiser when their ticket is replied to or
-- resolved.
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
    'LOAN_DECISION',
    'SUPPORT_TICKET_RAISED',
    'SUPPORT_TICKET_REPLY',
    'SUPPORT_TICKET_ESCALATED',
    'SUPPORT_TICKET_RESOLVED',
    'SUPPORT_TICKET_CLOSED',
    'SUPPORT_TICKET_REOPENED'
));
