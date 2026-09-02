-- A member raises an issue to their own co-op's admin; the admin resolves it directly, or
-- escalates it (with full history intact) to the super admin. An admin can also raise their own
-- issue straight to the super admin. Every ticket's full back-and-forth lives in
-- support_ticket_events — that IS the audit trail for the ticket, no separate log needed.
CREATE TABLE support_tickets (
    id                UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    subject           NVARCHAR(200)    NOT NULL,
    category          NVARCHAR(20)     NOT NULL,
    description       NVARCHAR(MAX)    NOT NULL,
    status            NVARCHAR(20)     NOT NULL CONSTRAINT DF_support_tickets_status DEFAULT 'Open',
    cooperative_id    NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    raised_by_id      NVARCHAR(20)     NOT NULL REFERENCES members(id),
    raised_by_role    NVARCHAR(20)     NOT NULL,
    -- Who currently owns resolving this — starts at 'admin' for a member's ticket, straight to
    -- 'super_admin' for an admin's own; escalation flips a member's ticket to 'super_admin' too.
    -- Never moves back once escalated.
    assigned_to_role  NVARCHAR(20)     NOT NULL,
    resolution_note   NVARCHAR(MAX)    NULL,
    created_at        DATETIME2        NOT NULL CONSTRAINT DF_support_tickets_created_at DEFAULT SYSUTCDATETIME(),
    resolved_at       DATETIME2        NULL,
    CONSTRAINT CK_support_tickets_category CHECK (category IN ('Savings', 'Loans', 'Account', 'Payments', 'Other')),
    -- 'Closed' is distinct from 'Resolved': Resolved means the issue was fixed, Closed means the
    -- assignee ended it without a fix (duplicate, not applicable, etc). Either can be reopened.
    CONSTRAINT CK_support_tickets_status CHECK (status IN ('Open', 'Escalated', 'Resolved', 'Closed')),
    CONSTRAINT CK_support_tickets_raised_by_role CHECK (raised_by_role IN ('member', 'admin')),
    CONSTRAINT CK_support_tickets_assigned_to_role CHECK (assigned_to_role IN ('admin', 'super_admin'))
);
CREATE INDEX IX_support_tickets_cooperative_id ON support_tickets(cooperative_id);
CREATE INDEX IX_support_tickets_raised_by_id ON support_tickets(raised_by_id);
CREATE INDEX IX_support_tickets_assigned_to_role ON support_tickets(assigned_to_role);

CREATE TABLE support_ticket_events (
    id           UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    ticket_id    UNIQUEIDENTIFIER NOT NULL REFERENCES support_tickets(id),
    event_type   NVARCHAR(20)     NOT NULL,
    actor_id     NVARCHAR(20)     NOT NULL REFERENCES members(id),
    actor_name   NVARCHAR(200)    NOT NULL,
    actor_role   NVARCHAR(20)     NOT NULL,
    message      NVARCHAR(MAX)    NULL,
    -- Optional evidence (a screenshot, a receipt) — Raised and Reply events only; a Cloudinary
    -- URL from the same POST /api/v1/uploads/attachment endpoint every other upload flow uses.
    attachment_url NVARCHAR(500)  NULL,
    created_at   DATETIME2        NOT NULL CONSTRAINT DF_support_ticket_events_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_support_ticket_events_type CHECK (event_type IN ('Raised', 'Reply', 'Escalated', 'Resolved', 'Closed', 'Reopened')),
    CONSTRAINT CK_support_ticket_events_actor_role CHECK (actor_role IN ('member', 'admin', 'super_admin'))
);
CREATE INDEX IX_support_ticket_events_ticket_id ON support_ticket_events(ticket_id);
