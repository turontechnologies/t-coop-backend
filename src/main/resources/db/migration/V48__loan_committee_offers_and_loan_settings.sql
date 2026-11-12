-- Loans, reworked around a co-op's own loan committee.
--
-- 1. The admin sets the loan committee: any number of people, in the order they approve. Each
--    one suggests an amount and a repayment period; the last one's figures are the approved terms.
-- 2. The approved terms go back to the borrower, who accepts (choosing where the money is paid:
--    their registered account, or another account in their own name) or declines.
-- 3. An accepted loan waits for disbursement (online transfer or cheque).
-- 4. Per-co-op loan rules (minimum amount, eligibility) and dividend rules.

CREATE TABLE loan_committee_members (
    id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    -- 1-based approval order.
    position        INT              NOT NULL,
    member_id       NVARCHAR(20)     NOT NULL REFERENCES members(id),
    -- What this approver is called on the loan, e.g. "Treasurer" or "Committee member".
    title           NVARCHAR(60)     NOT NULL,
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_loan_committee_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_loan_committee_position UNIQUE (cooperative_id, position),
    CONSTRAINT UQ_loan_committee_member UNIQUE (cooperative_id, member_id)
);
GO

-- Start every co-op with the committee it effectively had: its Vice President, Treasurer and
-- Secretary, in that order (where those offices are held).
INSERT INTO loan_committee_members (id, cooperative_id, position, member_id, title)
SELECT NEWID(), x.cooperative_id,
       ROW_NUMBER() OVER (PARTITION BY x.cooperative_id ORDER BY x.rank_order),
       x.member_id, x.title
FROM (
    SELECT r.cooperative_id, m.id AS member_id, r.name AS title,
           CASE r.office WHEN 'VICE_PRESIDENT' THEN 1 WHEN 'TREASURER' THEN 2 ELSE 3 END AS rank_order,
           ROW_NUMBER() OVER (PARTITION BY r.cooperative_id, m.id ORDER BY r.office) AS dup
    FROM coop_roles r
    JOIN members m ON m.coop_role_id = r.id AND m.cooperative_id = r.cooperative_id
    WHERE r.office IN ('VICE_PRESIDENT', 'TREASURER', 'SECRETARY') AND m.status = 'Active'
) x
WHERE x.dup = 1;
GO

-- Loan and dividend rules, per co-op.
ALTER TABLE cooperatives ADD
    loan_min_amount              DECIMAL(18,2) NOT NULL CONSTRAINT DF_coop_loan_min_amount DEFAULT 0,
    -- A "Times two" loan: up to this many times the member's savings.
    loan_savings_multiplier      DECIMAL(5,2)  NOT NULL CONSTRAINT DF_coop_loan_savings_multiplier DEFAULT 2,
    -- A "Within" loan: up to this percentage of the member's savings.
    loan_within_percent          DECIMAL(5,2)  NOT NULL CONSTRAINT DF_coop_loan_within_percent DEFAULT 100,
    loan_min_membership_months   INT           NOT NULL CONSTRAINT DF_coop_loan_min_membership DEFAULT 0,
    loan_min_savings             DECIMAL(18,2) NOT NULL CONSTRAINT DF_coop_loan_min_savings DEFAULT 0,
    -- Off: a member with a loan still running (or still in progress) can't apply for another.
    loan_allow_concurrent        BIT           NOT NULL CONSTRAINT DF_coop_loan_allow_concurrent DEFAULT 1,
    loan_min_guarantors          INT           NOT NULL CONSTRAINT DF_coop_loan_min_guarantors DEFAULT 2,
    dividend_method              NVARCHAR(20)  NOT NULL CONSTRAINT DF_coop_dividend_method DEFAULT 'SavingsPercent',
    dividend_rate                DECIMAL(6,2)  NOT NULL CONSTRAINT DF_coop_dividend_rate DEFAULT 2,
    dividend_frequency           NVARCHAR(20)  NOT NULL CONSTRAINT DF_coop_dividend_frequency DEFAULT 'Annually';
GO

ALTER TABLE cooperatives ADD
    CONSTRAINT CK_coop_dividend_method CHECK (dividend_method IN ('SavingsPercent', 'InterestShare', 'None')),
    CONSTRAINT CK_coop_dividend_frequency CHECK (dividend_frequency IN ('Monthly', 'Quarterly', 'Annually')),
    CONSTRAINT CK_coop_loan_min_guarantors CHECK (loan_min_guarantors BETWEEN 0 AND 10);
GO

-- What the borrower asked for (amount and months stay on the record as the live terms, which
-- each approver can change), where the committee is, the borrower's answer and payout account,
-- and how the loan was paid out.
ALTER TABLE loan_records ADD
    requested_amount        DECIMAL(18,2)  NULL,
    requested_months        INT            NULL,
    approval_step           INT            NULL,
    offer_responded_at      DATETIME2      NULL,
    payout_account_type     NVARCHAR(20)   NULL,
    payout_bank_code        NVARCHAR(20)   NULL,
    payout_bank_name        NVARCHAR(100)  NULL,
    payout_account_number   NVARCHAR(20)   NULL,
    payout_account_name     NVARCHAR(200)  NULL,
    payout_name_verified    BIT            NULL,
    disbursement_method     NVARCHAR(20)   NULL,
    cheque_url              NVARCHAR(500)  NULL,
    cheque_number           NVARCHAR(30)   NULL,
    disbursed_by_name       NVARCHAR(200)  NULL;
GO

UPDATE loan_records SET requested_amount = amount, requested_months = duration_months;

UPDATE loan_records SET approval_step = 1, status = 'Awaiting Approval' WHERE status = 'Awaiting Vice President';
UPDATE loan_records SET approval_step = 2, status = 'Awaiting Approval' WHERE status = 'Awaiting Treasurer';
UPDATE loan_records SET approval_step = 3, status = 'Awaiting Approval' WHERE status = 'Awaiting Secretary';

ALTER TABLE loan_records DROP CONSTRAINT CK_loan_records_status;
ALTER TABLE loan_records ADD CONSTRAINT CK_loan_records_status CHECK (status IN (
    'Awaiting Guarantor', 'Awaiting Approval', 'Awaiting Acceptance', 'Pending Disbursement',
    'Active', 'Completed', 'Rejected', 'Declined'
));
ALTER TABLE loan_records ADD CONSTRAINT CK_loan_records_payout_type
    CHECK (payout_account_type IS NULL OR payout_account_type IN ('Registered', 'Other'));
ALTER TABLE loan_records ADD CONSTRAINT CK_loan_records_disbursement_method
    CHECK (disbursement_method IS NULL OR disbursement_method IN ('Online', 'Cheque', 'Import'));
GO

-- Each approver's suggested amount and period. The step is now the committee title.
ALTER TABLE loan_approvals DROP CONSTRAINT CK_loan_approvals_step;
ALTER TABLE loan_approvals ALTER COLUMN step NVARCHAR(60) NOT NULL;
ALTER TABLE loan_approvals ADD
    position          INT            NULL,
    suggested_amount  DECIMAL(18,2)  NULL,
    suggested_months  INT            NULL,
    previous_amount   DECIMAL(18,2)  NULL,
    previous_months   INT            NULL;
GO
