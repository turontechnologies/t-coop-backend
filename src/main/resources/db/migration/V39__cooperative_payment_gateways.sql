-- A co-op admin's own payment gateway credentials (Settings -> Integrations), so members'
-- savings deposits and loan repayments settle straight into the co-op's own Paystack or
-- Flutterwave account instead of the platform's. One row per co-op, created on first save; a
-- co-op with no row (or nothing enabled) keeps falling back to the platform's Paystack keys
-- exactly as before this migration.
CREATE TABLE cooperative_payment_gateways (
    cooperative_id          NVARCHAR(20)   NOT NULL PRIMARY KEY REFERENCES cooperatives(id),
    paystack_enabled        BIT            NOT NULL CONSTRAINT DF_coop_gateways_paystack_enabled DEFAULT 0,
    paystack_public_key     NVARCHAR(200)  NULL,
    paystack_secret_key     NVARCHAR(200)  NULL,
    flutterwave_enabled     BIT            NOT NULL CONSTRAINT DF_coop_gateways_flutterwave_enabled DEFAULT 0,
    flutterwave_public_key  NVARCHAR(200)  NULL,
    flutterwave_secret_key  NVARCHAR(200)  NULL,
    updated_at              DATETIME2      NOT NULL CONSTRAINT DF_coop_gateways_updated_at DEFAULT SYSUTCDATETIME()
);

-- Which gateway (and whose credentials) a pending payment was started with, so confirm verifies
-- against the same account the member actually paid into — even if the admin changes their
-- gateway settings in between. Existing rows were all platform Paystack.
ALTER TABLE savings_payment_intents ADD
    gateway    NVARCHAR(20) NOT NULL CONSTRAINT DF_savings_payment_intents_gateway DEFAULT 'Paystack',
    key_owner  NVARCHAR(20) NOT NULL CONSTRAINT DF_savings_payment_intents_key_owner DEFAULT 'Platform';

ALTER TABLE loan_payment_intents ADD
    gateway    NVARCHAR(20) NOT NULL CONSTRAINT DF_loan_payment_intents_gateway DEFAULT 'Paystack',
    key_owner  NVARCHAR(20) NOT NULL CONSTRAINT DF_loan_payment_intents_key_owner DEFAULT 'Platform';

ALTER TABLE savings_records DROP CONSTRAINT CK_savings_records_method;
ALTER TABLE savings_records ADD CONSTRAINT CK_savings_records_method
    CHECK (method IN ('Paystack', 'Flutterwave', 'Manual Upload'));

ALTER TABLE loan_repayments DROP CONSTRAINT CK_loan_repayments_method;
ALTER TABLE loan_repayments ADD CONSTRAINT CK_loan_repayments_method
    CHECK (method IN ('Paystack', 'Flutterwave', 'Manual Upload'));
