-- Co-op officers and the loan approval chain.
--
-- Every co-op gets four fixed officer roles (President, Vice President, Treasurer, Secretary) as
-- coop_roles with an `office` set — assigned to members through the existing User Management
-- screen like any other role, one holder per office. A loan then moves
--   Awaiting Guarantor -> Awaiting Vice President -> Awaiting Treasurer -> Awaiting Secretary
-- and the Secretary's approval disburses it straight to the member's bank account (server-side
-- Paystack transfer), landing it in Active. The President can reject at any stage. When an
-- office is vacant, the co-op admin covers that step.

ALTER TABLE coop_roles ADD office NVARCHAR(20) NULL
    CONSTRAINT CK_coop_roles_office CHECK (office IN ('PRESIDENT', 'VICE_PRESIDENT', 'TREASURER', 'SECRETARY'));
GO

CREATE UNIQUE INDEX UQ_coop_roles_cooperative_office ON coop_roles(cooperative_id, office) WHERE office IS NOT NULL;

-- A co-op that already made its own "Treasurer" (etc.) role keeps it — it just becomes the office.
UPDATE r SET office = o.office
FROM coop_roles r
JOIN (VALUES ('President', 'PRESIDENT'),
             ('Vice President', 'VICE_PRESIDENT'),
             ('Treasurer', 'TREASURER'),
             ('Secretary', 'SECRETARY')) o(name, office)
  ON r.name = o.name;

INSERT INTO coop_roles (id, cooperative_id, name, permissions, status, office)
SELECT NEWID(), c.id, o.name,
       'Dashboard::read,Members Directory::read,Loans::Requests::write,Loans::Members Loans::read,Loans::My Loans::write,Savings & Contributions::My Savings::write,Notice Board::read,Support::write,Settings::Profile::write',
       'Active', o.office
FROM cooperatives c
CROSS JOIN (VALUES ('President', 'PRESIDENT'),
                   ('Vice President', 'VICE_PRESIDENT'),
                   ('Treasurer', 'TREASURER'),
                   ('Secretary', 'SECRETARY')) o(name, office)
WHERE NOT EXISTS (
    SELECT 1 FROM coop_roles r
    WHERE r.cooperative_id = c.id AND (r.office = o.office OR r.name = o.name));

-- Loan statuses: the single "Awaiting Admin" step becomes the three-officer chain.
ALTER TABLE loan_records DROP CONSTRAINT CK_loan_records_status;

UPDATE loan_records SET status = 'Awaiting Vice President' WHERE status = 'Awaiting Admin';

ALTER TABLE loan_records ADD CONSTRAINT CK_loan_records_status CHECK (status IN (
    'Awaiting Guarantor', 'Awaiting Vice President', 'Awaiting Treasurer', 'Awaiting Secretary',
    'Active', 'Completed', 'Rejected',
    'Awaiting Approval' -- legacy personal-loan flow status, kept for old rows
));

-- What actually left the co-op's account on disbursement (principal minus loan charges), and the
-- Paystack transfer that carried it. The reference is derived from the loan id, so a retried
-- approval can never pay the same loan twice.
ALTER TABLE loan_records ADD
    disbursement_reference  NVARCHAR(100)  NULL,
    disbursement_status     NVARCHAR(20)   NULL,
    disbursed_amount        DECIMAL(18,2)  NULL,
    disbursed_at            DATETIME2      NULL;

-- One row per officer decision — the loan's approval trail.
CREATE TABLE loan_approvals (
    id           UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    loan_id      UNIQUEIDENTIFIER NOT NULL REFERENCES loan_records(id),
    step         NVARCHAR(20)     NOT NULL,
    decision     NVARCHAR(20)     NOT NULL,
    member_id    NVARCHAR(20)     NOT NULL REFERENCES members(id),
    member_name  NVARCHAR(200)    NOT NULL,
    -- True when the co-op admin acted because the office was vacant.
    covering     BIT              NOT NULL CONSTRAINT DF_loan_approvals_covering DEFAULT 0,
    note         NVARCHAR(1000)   NULL,
    acted_at     DATETIME2        NOT NULL CONSTRAINT DF_loan_approvals_acted_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loan_approvals_step CHECK (step IN ('PRESIDENT', 'VICE_PRESIDENT', 'TREASURER', 'SECRETARY')),
    CONSTRAINT CK_loan_approvals_decision CHECK (decision IN ('Approved', 'Rejected'))
);
CREATE INDEX IX_loan_approvals_loan_id ON loan_approvals(loan_id);

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
    'LOAN_APPROVAL_REQUEST',
    'LOAN_DISBURSED',
    'SUPPORT_TICKET_RAISED',
    'SUPPORT_TICKET_REPLY',
    'SUPPORT_TICKET_ESCALATED',
    'SUPPORT_TICKET_RESOLVED',
    'SUPPORT_TICKET_CLOSED',
    'SUPPORT_TICKET_REOPENED'
));
