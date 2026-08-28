-- Withdrawal fee (both the co-op's own and the platform's) becomes Fixed-or-Percentage, same
-- shape as the existing savings/loans charges — "percent" was never the only option members
-- actually wanted charged. Renamed *_withdrawal_fee_percent -> *_withdrawal_fee_amount (now
-- DECIMAL(18,2) like the other charge amount columns, since a Fixed fee isn't capped at 999.99)
-- and a new *_withdrawal_fee_type column decides how the amount is interpreted. The new column
-- and its CHECK constraint are added in the SAME ALTER TABLE statement — SQL Server can't resolve
-- a column in a CHECK constraint added by a later, separate ALTER TABLE statement in the same
-- migration.

ALTER TABLE cooperatives DROP CONSTRAINT DF_cooperatives_withdrawal_fee;
EXEC sp_rename 'cooperatives.withdrawal_fee_percent', 'withdrawal_fee_amount', 'COLUMN';
ALTER TABLE cooperatives ALTER COLUMN withdrawal_fee_amount DECIMAL(18,2) NOT NULL;
ALTER TABLE cooperatives ADD CONSTRAINT DF_cooperatives_withdrawal_fee_amount DEFAULT 0 FOR withdrawal_fee_amount;
ALTER TABLE cooperatives ADD
    withdrawal_fee_type NVARCHAR(20) NOT NULL CONSTRAINT DF_cooperatives_withdrawal_fee_type DEFAULT 'Percentage',
    CONSTRAINT CK_cooperatives_withdrawal_fee_type CHECK (withdrawal_fee_type IN ('Fixed', 'Percentage'));

ALTER TABLE platform_fee_settings DROP CONSTRAINT DF_platform_fee_withdrawal_fee;
EXEC sp_rename 'platform_fee_settings.withdrawal_fee_percent', 'withdrawal_fee_amount', 'COLUMN';
ALTER TABLE platform_fee_settings ALTER COLUMN withdrawal_fee_amount DECIMAL(18,2) NOT NULL;
ALTER TABLE platform_fee_settings ADD CONSTRAINT DF_platform_fee_withdrawal_fee_amount DEFAULT 1 FOR withdrawal_fee_amount;
ALTER TABLE platform_fee_settings ADD
    withdrawal_fee_type NVARCHAR(20) NOT NULL CONSTRAINT DF_platform_fee_withdrawal_fee_type DEFAULT 'Percentage',
    CONSTRAINT CK_platform_fee_withdrawal_fee_type CHECK (withdrawal_fee_type IN ('Fixed', 'Percentage'));
