-- The platform's share of a loan application fee can now be a fixed amount or a percentage
-- (it was always a percentage in V55), and each co-operative has its own subscription grace
-- period. The super admin's values in platform_fee_settings become the defaults a new co-op
-- starts with.

EXEC sp_rename 'cooperatives.platform_loan_fee_percent', 'platform_loan_fee_amount', 'COLUMN';
EXEC sp_rename 'platform_fee_settings.loan_application_fee_percent', 'loan_application_fee_amount', 'COLUMN';

ALTER TABLE cooperatives ADD
    platform_loan_fee_type   VARCHAR(20) NOT NULL CONSTRAINT DF_cooperatives_platform_loan_fee_type DEFAULT 'Percentage',
    subscription_grace_days  INT         NOT NULL CONSTRAINT DF_cooperatives_subscription_grace_days DEFAULT 0;

ALTER TABLE platform_fee_settings ADD
    loan_application_fee_type VARCHAR(20) NOT NULL CONSTRAINT DF_platform_fee_loan_application_fee_type DEFAULT 'Percentage';
GO

-- Existing co-ops keep the grace period that applied to them until now.
UPDATE cooperatives
SET subscription_grace_days = COALESCE((SELECT subscription_grace_days FROM platform_fee_settings WHERE id = 1), 0);
