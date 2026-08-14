-- Baseline schema. See documentation/schema-design.md for the reasoning
-- behind these tables. Applied automatically by Flyway on startup — never
-- edit this file after it's been run against a shared database; add a new
-- V2__..., V3__... migration instead.

-- ============================================================================
-- Co-operatives
-- ============================================================================
CREATE TABLE cooperatives (
    id                       NVARCHAR(20)    NOT NULL PRIMARY KEY,
    name                     NVARCHAR(200)   NOT NULL,
    admin_name               NVARCHAR(200)   NOT NULL,
    contact_email            NVARCHAR(320)   NOT NULL,
    contact_phone            NVARCHAR(30)    NOT NULL,
    address                  NVARCHAR(400)   NOT NULL,
    country                  NVARCHAR(100)   NOT NULL,
    state                    NVARCHAR(100)   NOT NULL,
    city                     NVARCHAR(100)   NOT NULL,
    status                   NVARCHAR(20)    NOT NULL CONSTRAINT DF_cooperatives_status DEFAULT 'Active',
    currency                 NVARCHAR(3)     NOT NULL CONSTRAINT DF_cooperatives_currency DEFAULT 'NGN',
    subscription_fee         DECIMAL(18,2)   NOT NULL CONSTRAINT DF_cooperatives_subscription_fee DEFAULT 0,
    withdrawal_fee_percent   DECIMAL(5,2)    NOT NULL CONSTRAINT DF_cooperatives_withdrawal_fee DEFAULT 0,
    created_at               DATETIME2       NOT NULL CONSTRAINT DF_cooperatives_created_at DEFAULT SYSUTCDATETIME(),
    updated_at               DATETIME2       NOT NULL CONSTRAINT DF_cooperatives_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_cooperatives_status CHECK (status IN ('Active', 'Disabled'))
);

-- ============================================================================
-- Members (all three roles — super_admin, admin, member)
-- ============================================================================
CREATE TABLE members (
    id                  NVARCHAR(20)    NOT NULL PRIMARY KEY, -- membership ID, also the login ID
    cooperative_id      NVARCHAR(20)    NULL REFERENCES cooperatives(id), -- null for super_admin
    role                NVARCHAR(20)    NOT NULL,
    password_hash       NVARCHAR(200)   NOT NULL,
    first_name          NVARCHAR(100)   NOT NULL,
    last_name           NVARCHAR(100)   NOT NULL,
    other_name          NVARCHAR(100)   NULL,
    email               NVARCHAR(320)   NOT NULL UNIQUE,
    phone               NVARCHAR(30)    NULL,
    gender              NVARCHAR(10)    NULL,
    nin                 NVARCHAR(20)    NULL,
    home_address        NVARCHAR(400)   NULL,
    country             NVARCHAR(100)   NULL,
    state               NVARCHAR(100)   NULL,
    city                NVARCHAR(100)   NULL,
    facebook            NVARCHAR(200)   NULL,
    twitter             NVARCHAR(200)   NULL,
    guarantor           NVARCHAR(200)   NULL,
    bank_code           NVARCHAR(10)    NULL,
    account_number      NVARCHAR(20)    NULL,
    account_name        NVARCHAR(200)   NULL,
    avatar_url          NVARCHAR(500)   NULL,
    status              NVARCHAR(20)    NOT NULL CONSTRAINT DF_members_status DEFAULT 'Active',
    created_at          DATETIME2       NOT NULL CONSTRAINT DF_members_created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL CONSTRAINT DF_members_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_members_role CHECK (role IN ('super_admin', 'admin', 'member')),
    CONSTRAINT CK_members_status CHECK (status IN ('Active', 'Inactive'))
);
CREATE INDEX IX_members_cooperative_id ON members(cooperative_id);

-- ============================================================================
-- Savings & loan type catalogs (per co-op — an admin can define their own)
-- ============================================================================
CREATE TABLE savings_types (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    name            NVARCHAR(100)    NOT NULL,
    min_amount      DECIMAL(18,2)    NOT NULL,
    max_amount      DECIMAL(18,2)    NOT NULL,
    status          NVARCHAR(20)     NOT NULL CONSTRAINT DF_savings_types_status DEFAULT 'Active',
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_savings_types_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_savings_types_status CHECK (status IN ('Active', 'Inactive'))
);
CREATE INDEX IX_savings_types_cooperative_id ON savings_types(cooperative_id);

CREATE TABLE savings_type_approvers (
    savings_type_id UNIQUEIDENTIFIER NOT NULL REFERENCES savings_types(id),
    member_id       NVARCHAR(20)     NOT NULL REFERENCES members(id),
    PRIMARY KEY (savings_type_id, member_id)
);

CREATE TABLE loan_types (
    id                    UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id        NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    name                  NVARCHAR(100)    NOT NULL,
    eligibility_percent    DECIMAL(6,2)     NOT NULL,
    duration_months       INT              NOT NULL,
    max_amount            DECIMAL(18,2)    NOT NULL,
    repayment_interval    NVARCHAR(20)     NOT NULL,
    number_of_installments INT             NOT NULL,
    interest_type         NVARCHAR(20)     NOT NULL,
    interest_amount       DECIMAL(18,2)    NOT NULL,
    approver1_id          NVARCHAR(20)     NULL REFERENCES members(id),
    approver2_id          NVARCHAR(20)     NULL REFERENCES members(id),
    loan_terms            NVARCHAR(MAX)    NULL,
    guarantor_terms       NVARCHAR(MAX)    NULL,
    status                NVARCHAR(20)     NOT NULL CONSTRAINT DF_loan_types_status DEFAULT 'Active',
    created_at            DATETIME2        NOT NULL CONSTRAINT DF_loan_types_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loan_types_status CHECK (status IN ('Active', 'Inactive')),
    CONSTRAINT CK_loan_types_repayment_interval CHECK (repayment_interval IN ('Weekly', 'Monthly', 'Quarterly')),
    CONSTRAINT CK_loan_types_interest_type CHECK (interest_type IN ('Percentage', 'Fixed'))
);
CREATE INDEX IX_loan_types_cooperative_id ON loan_types(cooperative_id);

-- ============================================================================
-- Savings
-- ============================================================================
CREATE TABLE savings_records (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id       NVARCHAR(20)     NOT NULL REFERENCES members(id),
    savings_type_id UNIQUEIDENTIFIER NOT NULL REFERENCES savings_types(id),
    amount          DECIMAL(18,2)    NOT NULL, -- negative for withdrawals
    balance_after   DECIMAL(18,2)    NOT NULL, -- computed server-side, never client-supplied
    method          NVARCHAR(20)     NOT NULL,
    transaction_id  NVARCHAR(100)    NOT NULL,
    record_date     DATE             NOT NULL,
    status          NVARCHAR(20)     NOT NULL,
    receipt_url     NVARCHAR(500)    NULL,
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_savings_records_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_savings_records_method CHECK (method IN ('Paystack', 'Manual Upload')),
    CONSTRAINT CK_savings_records_status CHECK (status IN ('Success', 'Pending', 'Failed'))
);
CREATE INDEX IX_savings_records_cooperative_id ON savings_records(cooperative_id);
CREATE INDEX IX_savings_records_member_id ON savings_records(member_id);

CREATE TABLE savings_requests (
    id               UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id   NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id        NVARCHAR(20)     NOT NULL REFERENCES members(id),
    request_type     NVARCHAR(20)     NOT NULL,
    savings_type_id  UNIQUEIDENTIFIER NULL REFERENCES savings_types(id), -- null = "Total Savings" (all types)
    amount           DECIMAL(18,2)    NOT NULL,
    note             NVARCHAR(1000)   NULL,
    status           NVARCHAR(20)     NOT NULL CONSTRAINT DF_savings_requests_status DEFAULT 'Pending',
    fee_percent      DECIMAL(5,2)     NULL,
    fee_amount       DECIMAL(18,2)    NULL,
    net_amount       DECIMAL(18,2)    NULL,
    requested_at     DATETIME2        NOT NULL CONSTRAINT DF_savings_requests_requested_at DEFAULT SYSUTCDATETIME(),
    resolved_at      DATETIME2        NULL,
    CONSTRAINT CK_savings_requests_type CHECK (request_type IN ('Deposit', 'Withdrawal')),
    CONSTRAINT CK_savings_requests_status CHECK (status IN ('Pending', 'Approved', 'Declined'))
);
CREATE INDEX IX_savings_requests_cooperative_id ON savings_requests(cooperative_id);
CREATE INDEX IX_savings_requests_member_id ON savings_requests(member_id);

-- ============================================================================
-- Loans
-- ============================================================================
CREATE TABLE loan_records (
    id                     UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id         NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id              NVARCHAR(20)     NOT NULL REFERENCES members(id),
    loan_type_id           UNIQUEIDENTIFIER NOT NULL REFERENCES loan_types(id),
    amount                 DECIMAL(18,2)    NOT NULL,
    interest_rate          DECIMAL(6,2)     NOT NULL,
    duration_months        INT              NOT NULL,
    number_of_repayments   INT              NOT NULL,
    monthly_repayment      DECIMAL(18,2)    NOT NULL,
    total_repayment        DECIMAL(18,2)    NOT NULL,
    guarantor_id           NVARCHAR(20)     NULL REFERENCES members(id),
    guarantor_name         NVARCHAR(200)    NOT NULL,
    loan_date              DATE             NOT NULL,
    status                 NVARCHAR(30)     NOT NULL,
    repayments_made        INT              NOT NULL CONSTRAINT DF_loan_records_repayments_made DEFAULT 0,
    guarantor_document_url NVARCHAR(500)    NULL,
    guarantor_accepted_at  DATETIME2        NULL,
    rejection_reason       NVARCHAR(1000)   NULL,
    created_at             DATETIME2        NOT NULL CONSTRAINT DF_loan_records_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loan_records_status CHECK (status IN (
        'Awaiting Guarantor', 'Awaiting Admin', 'Active', 'Completed', 'Rejected',
        'Awaiting Approval' -- legacy personal-loan flow status from the mock
    ))
);
CREATE INDEX IX_loan_records_cooperative_id ON loan_records(cooperative_id);
CREATE INDEX IX_loan_records_member_id ON loan_records(member_id);

-- ============================================================================
-- Notices
-- ============================================================================
CREATE TABLE notices (
    id               UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    notice_type      NVARCHAR(30)     NOT NULL,
    title            NVARCHAR(300)    NOT NULL,
    message          NVARCHAR(MAX)    NOT NULL,
    recipient        NVARCHAR(30)     NOT NULL,
    medium           NVARCHAR(20)     NOT NULL,
    meeting_date     DATE             NULL,
    attachment_name  NVARCHAR(300)    NULL,
    attachment_url   NVARCHAR(500)    NULL,
    attachment_size  INT              NULL,
    send_at          DATETIME2        NOT NULL,
    created_by_id    NVARCHAR(20)     NOT NULL REFERENCES members(id),
    created_by_role  NVARCHAR(20)     NOT NULL, -- snapshot at creation time
    created_at       DATETIME2        NOT NULL CONSTRAINT DF_notices_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_notices_type CHECK (notice_type IN ('General', 'Meeting Notice', 'Meeting Minutes')),
    CONSTRAINT CK_notices_recipient CHECK (recipient IN ('All Members', 'All Admins', 'All Members & Admins')),
    CONSTRAINT CK_notices_medium CHECK (medium IN ('Email', 'SMS', 'Email & SMS'))
);
CREATE INDEX IX_notices_send_at ON notices(send_at);

-- No rows for a notice here = platform-wide broadcast (visible to every co-op).
CREATE TABLE notice_target_cooperatives (
    notice_id       UNIQUEIDENTIFIER NOT NULL REFERENCES notices(id),
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    PRIMARY KEY (notice_id, cooperative_id)
);

CREATE TABLE notice_replies (
    id            UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    notice_id     UNIQUEIDENTIFIER NOT NULL REFERENCES notices(id),
    author_id     NVARCHAR(20)     NOT NULL REFERENCES members(id),
    author_role   NVARCHAR(20)     NOT NULL, -- snapshot at reply time
    message       NVARCHAR(2000)   NOT NULL,
    created_at    DATETIME2        NOT NULL CONSTRAINT DF_notice_replies_created_at DEFAULT SYSUTCDATETIME()
);
CREATE INDEX IX_notice_replies_notice_id ON notice_replies(notice_id);

CREATE TABLE notice_read_receipts (
    notice_id  UNIQUEIDENTIFIER NOT NULL REFERENCES notices(id),
    member_id  NVARCHAR(20)     NOT NULL REFERENCES members(id),
    read_at    DATETIME2        NOT NULL CONSTRAINT DF_notice_read_receipts_read_at DEFAULT SYSUTCDATETIME(),
    PRIMARY KEY (notice_id, member_id)
);

-- ============================================================================
-- Subscriptions (co-op -> platform payments)
-- ============================================================================
CREATE TABLE subscription_payments (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    payment_ref     NVARCHAR(100)    NOT NULL,
    amount_paid     DECIMAL(18,2)    NOT NULL,
    method          NVARCHAR(20)     NOT NULL,
    payment_date    DATE             NOT NULL,
    narration       NVARCHAR(500)    NULL,
    status          NVARCHAR(20)     NOT NULL,
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_subscription_payments_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_subscription_payments_method CHECK (method IN ('Manual', 'Paystack')),
    CONSTRAINT CK_subscription_payments_status CHECK (status IN ('Active', 'Overdue'))
);
CREATE INDEX IX_subscription_payments_cooperative_id ON subscription_payments(cooperative_id);

-- ============================================================================
-- Platform-level settings (single row)
-- ============================================================================
CREATE TABLE platform_fee_settings (
    id                       INT             NOT NULL PRIMARY KEY CONSTRAINT DF_platform_fee_settings_id DEFAULT 1,
    savings_charge_type      NVARCHAR(20)    NOT NULL CONSTRAINT DF_platform_fee_savings_charge_type DEFAULT 'Percentage',
    savings_charge_amount    DECIMAL(18,2)   NOT NULL CONSTRAINT DF_platform_fee_savings_charge_amount DEFAULT 0.25,
    loans_charge_type        NVARCHAR(20)    NOT NULL CONSTRAINT DF_platform_fee_loans_charge_type DEFAULT 'Percentage',
    loans_charge_amount      DECIMAL(18,2)   NOT NULL CONSTRAINT DF_platform_fee_loans_charge_amount DEFAULT 1,
    withdrawal_fee_percent   DECIMAL(5,2)    NOT NULL CONSTRAINT DF_platform_fee_withdrawal_fee DEFAULT 1,
    updated_at               DATETIME2       NOT NULL CONSTRAINT DF_platform_fee_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_platform_fee_settings_singleton CHECK (id = 1),
    CONSTRAINT CK_platform_fee_savings_charge_type CHECK (savings_charge_type IN ('Fixed', 'Percentage')),
    CONSTRAINT CK_platform_fee_loans_charge_type CHECK (loans_charge_type IN ('Fixed', 'Percentage'))
);
INSERT INTO platform_fee_settings (id) VALUES (1);

-- ============================================================================
-- Auth support
-- ============================================================================
CREATE TABLE password_reset_tokens (
    id           UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    email        NVARCHAR(320)    NOT NULL,
    otp_hash     NVARCHAR(200)    NOT NULL,
    reset_token  NVARCHAR(200)    NULL, -- set once the OTP is verified
    expires_at   DATETIME2        NOT NULL,
    used         BIT              NOT NULL CONSTRAINT DF_password_reset_tokens_used DEFAULT 0,
    created_at   DATETIME2        NOT NULL CONSTRAINT DF_password_reset_tokens_created_at DEFAULT SYSUTCDATETIME()
);
CREATE INDEX IX_password_reset_tokens_email ON password_reset_tokens(email);

-- ============================================================================
-- Audit log
-- ============================================================================
CREATE TABLE audit_log (
    id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    actor_id     NVARCHAR(20)     NULL REFERENCES members(id),
    actor_role   NVARCHAR(20)     NULL,
    module       NVARCHAR(50)     NOT NULL,
    action       NVARCHAR(50)     NOT NULL,
    resource     NVARCHAR(500)    NULL,
    status       NVARCHAR(20)     NOT NULL CONSTRAINT DF_audit_log_status DEFAULT 'Success',
    ip_address   NVARCHAR(50)     NULL,
    created_at   DATETIME2        NOT NULL CONSTRAINT DF_audit_log_created_at DEFAULT SYSUTCDATETIME()
);
CREATE INDEX IX_audit_log_created_at ON audit_log(created_at);
