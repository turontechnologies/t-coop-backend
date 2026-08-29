-- Gives each co-op its own savings-deposit and loan-disbursement charge rate, mirroring the
-- existing withdrawal_fee_amount/withdrawal_fee_type columns. Until now only the withdrawal fee
-- was actually split between a co-op and the platform; the platform's own savings/loans charge
-- rates (PlatformSettings.savingsChargeAmount/loansChargeAmount) existed in Fees & Charges but
-- were never deducted anywhere. Both charge types now combine a co-op rate with the platform
-- rate, same mechanics as withdrawal fee.
ALTER TABLE cooperatives ADD
    savings_charge_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_cooperatives_savings_charge_amount DEFAULT 0,
    savings_charge_type   NVARCHAR(20)  NOT NULL CONSTRAINT DF_cooperatives_savings_charge_type DEFAULT 'Percentage',
    loans_charge_amount   DECIMAL(18,2) NOT NULL CONSTRAINT DF_cooperatives_loans_charge_amount DEFAULT 0,
    loans_charge_type     NVARCHAR(20)  NOT NULL CONSTRAINT DF_cooperatives_loans_charge_type DEFAULT 'Percentage';
