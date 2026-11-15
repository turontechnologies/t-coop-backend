-- The platform's own cut is now set per co-operative by the super admin, instead of one rate for
-- every co-op. Each co-op starts with whatever the platform-wide rate was, so nothing changes
-- for existing co-ops until the super admin edits them. The platform_fee_settings values stay as
-- the defaults a newly created co-op starts with.
ALTER TABLE cooperatives ADD
    platform_savings_charge_type   NVARCHAR(20)  NOT NULL CONSTRAINT DF_cooperatives_platform_savings_charge_type DEFAULT 'Percentage',
    platform_savings_charge_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_cooperatives_platform_savings_charge_amount DEFAULT 0,
    platform_loans_charge_type     NVARCHAR(20)  NOT NULL CONSTRAINT DF_cooperatives_platform_loans_charge_type DEFAULT 'Percentage',
    platform_loans_charge_amount   DECIMAL(18,2) NOT NULL CONSTRAINT DF_cooperatives_platform_loans_charge_amount DEFAULT 0,
    platform_withdrawal_fee_type   NVARCHAR(20)  NOT NULL CONSTRAINT DF_cooperatives_platform_withdrawal_fee_type DEFAULT 'Percentage',
    platform_withdrawal_fee_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_cooperatives_platform_withdrawal_fee_amount DEFAULT 0,
    -- The platform's percentage of every loan application fee the co-op collects.
    platform_loan_fee_percent      DECIMAL(9,4)  NOT NULL CONSTRAINT DF_cooperatives_platform_loan_fee_percent DEFAULT 0;
GO

UPDATE c SET
    platform_savings_charge_type   = s.savings_charge_type,
    platform_savings_charge_amount = s.savings_charge_amount,
    platform_loans_charge_type     = s.loans_charge_type,
    platform_loans_charge_amount   = s.loans_charge_amount,
    platform_withdrawal_fee_type   = s.withdrawal_fee_type,
    platform_withdrawal_fee_amount = s.withdrawal_fee_amount
FROM cooperatives c CROSS JOIN platform_fee_settings s
WHERE s.id = 1;
GO

ALTER TABLE platform_fee_settings ADD
    loan_application_fee_percent DECIMAL(9,4) NOT NULL CONSTRAINT DF_platform_fee_loan_application_fee_percent DEFAULT 0,
    -- Days a co-op keeps working after its subscription expires, before it is blocked.
    subscription_grace_days      INT          NOT NULL CONSTRAINT DF_platform_fee_subscription_grace_days DEFAULT 0;
GO

-- A withdrawal's fee is the co-op's and the platform's together; keep the platform's share so
-- it can be recorded as earned when the withdrawal is paid out.
ALTER TABLE savings_requests ADD platform_fee_amount DECIMAL(18,2) NULL;
GO

-- Every fee the platform earns, one row per charge, so the super admin can see what came from
-- each co-operative. Source: SavingsCharge, LoanCharge, WithdrawalFee or LoanApplicationFee.
CREATE TABLE platform_earnings (
    id             UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    cooperative_id NVARCHAR(50)     NOT NULL,
    member_id      NVARCHAR(50)     NULL,
    source         NVARCHAR(30)     NOT NULL,
    base_amount    DECIMAL(18,2)    NOT NULL,
    amount         DECIMAL(18,2)    NOT NULL,
    reference      NVARCHAR(150)    NOT NULL,
    created_at     DATETIME2        NOT NULL CONSTRAINT DF_platform_earnings_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_platform_earnings_source_reference UNIQUE (source, reference)
);
CREATE INDEX IX_platform_earnings_cooperative_id ON platform_earnings(cooperative_id, created_at);
GO
