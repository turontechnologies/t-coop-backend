-- New members can apply to join a co-op themselves. Each co-op chooses whether applications are
-- open, its rules and what to bring, and the joining fee: a form fee (the co-op keeps it) plus a
-- first savings amount (paid into the new member's savings when they are approved).
CREATE TABLE registration_settings (
    cooperative_id       NVARCHAR(20)     NOT NULL PRIMARY KEY REFERENCES cooperatives(id),
    is_open              BIT              NOT NULL CONSTRAINT DF_registration_settings_is_open DEFAULT 0,
    form_fee             DECIMAL(18,2)    NOT NULL CONSTRAINT DF_registration_settings_form_fee DEFAULT 0,
    first_savings        DECIMAL(18,2)    NOT NULL CONSTRAINT DF_registration_settings_first_savings DEFAULT 0,
    savings_type_id      UNIQUEIDENTIFIER NULL,
    rules                NVARCHAR(MAX)    NULL,
    -- What to have ready, one item per line.
    requirements         NVARCHAR(MAX)    NULL,
    require_id_document  BIT              NOT NULL CONSTRAINT DF_registration_settings_require_id DEFAULT 1,
    min_age              INT              NOT NULL CONSTRAINT DF_registration_settings_min_age DEFAULT 18,
    updated_at           DATETIME2        NOT NULL CONSTRAINT DF_registration_settings_updated_at DEFAULT SYSUTCDATETIME()
);
GO

-- Draft (being filled in, or payment started) -> Submitted (paid, waiting for the admin)
-- -> Approved (now a member) or Rejected.
CREATE TABLE membership_applications (
    id                       UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    reference                NVARCHAR(20)     NOT NULL,
    cooperative_id           NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    status                   NVARCHAR(20)     NOT NULL,
    access_token             NVARCHAR(64)     NULL,
    email_code_hash          NVARCHAR(200)    NULL,
    email_code_expires_at    DATETIME2        NULL,
    email_code_attempts      INT              NOT NULL CONSTRAINT DF_membership_applications_code_attempts DEFAULT 0,
    email_verified_at        DATETIME2        NULL,
    first_name               NVARCHAR(100)    NOT NULL,
    last_name                NVARCHAR(100)    NOT NULL,
    other_name               NVARCHAR(100)    NULL,
    gender                   NVARCHAR(20)     NULL,
    date_of_birth            DATE             NULL,
    email                    NVARCHAR(320)    NOT NULL,
    phone                    NVARCHAR(30)     NOT NULL,
    nin                      NVARCHAR(20)     NULL,
    occupation               NVARCHAR(150)    NULL,
    home_address             NVARCHAR(400)    NULL,
    country                  NVARCHAR(100)    NULL,
    state                    NVARCHAR(100)    NULL,
    city                     NVARCHAR(100)    NULL,
    next_of_kin_name         NVARCHAR(200)    NULL,
    next_of_kin_phone        NVARCHAR(30)     NULL,
    next_of_kin_relationship NVARCHAR(50)     NULL,
    bank_code                NVARCHAR(20)     NULL,
    account_number           NVARCHAR(20)     NULL,
    account_name             NVARCHAR(200)    NULL,
    -- The name the bank gave back for that account number, when we could look it up.
    bank_account_name        NVARCHAR(200)    NULL,
    photo_url                NVARCHAR(500)    NULL,
    id_document_url          NVARCHAR(500)    NULL,
    id_document_type         NVARCHAR(50)     NULL,
    form_fee                 DECIMAL(18,2)    NULL,
    first_savings            DECIMAL(18,2)    NULL,
    payment_reference        NVARCHAR(100)    NULL,
    payment_gateway          NVARCHAR(20)     NULL,
    payment_key_owner        NVARCHAR(20)     NULL,
    amount_paid              DECIMAL(18,2)    NULL,
    paid_at                  DATETIME2        NULL,
    submitted_at             DATETIME2        NULL,
    decided_at               DATETIME2        NULL,
    decided_by               NVARCHAR(20)     NULL,
    decision_note            NVARCHAR(1000)   NULL,
    member_id                NVARCHAR(20)     NULL,
    created_at               DATETIME2        NOT NULL CONSTRAINT DF_membership_applications_created_at DEFAULT SYSUTCDATETIME(),
    updated_at               DATETIME2        NOT NULL CONSTRAINT DF_membership_applications_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_membership_applications_status CHECK (status IN ('Draft', 'Submitted', 'Approved', 'Rejected'))
);
CREATE UNIQUE INDEX UX_membership_applications_reference ON membership_applications(reference);
CREATE INDEX IX_membership_applications_coop_status ON membership_applications(cooperative_id, status);
CREATE INDEX IX_membership_applications_email ON membership_applications(email);
CREATE INDEX IX_membership_applications_payment ON membership_applications(payment_reference);
GO

-- Who the applicant named as guarantors. A guarantor who is a member is asked "do you know this
-- person?" in the app; someone from outside is only recorded.
CREATE TABLE membership_application_guarantors (
    id                   UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    application_id       UNIQUEIDENTIFIER NOT NULL REFERENCES membership_applications(id) ON DELETE CASCADE,
    guarantor_member_id  NVARCHAR(20)     NULL,
    name                 NVARCHAR(200)    NOT NULL,
    phone                NVARCHAR(30)     NULL,
    email                NVARCHAR(320)    NULL,
    status               NVARCHAR(20)     NOT NULL,
    responded_at         DATETIME2        NULL,
    created_at           DATETIME2        NOT NULL CONSTRAINT DF_membership_application_guarantors_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_membership_application_guarantors_status CHECK (status IN ('Pending', 'Confirmed', 'Declined', 'NotAsked'))
);
CREATE INDEX IX_membership_application_guarantors_app ON membership_application_guarantors(application_id);
CREATE INDEX IX_membership_application_guarantors_member ON membership_application_guarantors(guarantor_member_id, status);
GO

ALTER TABLE notifications DROP CONSTRAINT CK_notifications_type;
GO

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
    'MEMBER_GUARANTOR',
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
    'SUPPORT_TICKET_REOPENED',
    'SAVINGS_WITHDRAWAL_REQUESTED',
    'SAVINGS_WITHDRAWAL_APPROVED',
    'SAVINGS_WITHDRAWAL_DECLINED',
    'LOAN_TRANSFER_REPAYMENT_SUBMITTED',
    'LOAN_TRANSFER_REPAYMENT_CONFIRMED',
    'LOAN_TRANSFER_REPAYMENT_REJECTED',
    'MEMBERSHIP_APPLICATION_RECEIVED',
    'MEMBERSHIP_GUARANTOR_REQUEST',
    'MEMBERSHIP_GUARANTOR_RESPONSE'
));
GO
