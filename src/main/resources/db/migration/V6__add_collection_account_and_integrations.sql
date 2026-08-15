-- Extends the existing platform-level settings singleton (platform_fee_settings,
-- one row, id = 1 — see V1) with the two other super-admin "Payment Settings" /
-- "Integrations" tabs: the platform's own collections bank account, and the
-- Paystack/Flutterwave credential fields. Kept on the same singleton row rather
-- than new tables since it's still one platform, one settings blob — matches
-- the reasoning already documented for platform_fee_settings in schema-design.md.

ALTER TABLE platform_fee_settings ADD
    collection_bank_code         NVARCHAR(10)   NULL,
    collection_account_number    NVARCHAR(20)   NULL,
    collection_account_name      NVARCHAR(200)  NULL,
    paystack_enabled              BIT            NOT NULL CONSTRAINT DF_platform_fee_paystack_enabled DEFAULT 1,
    paystack_public_key           NVARCHAR(200)  NULL,
    paystack_secret_key           NVARCHAR(200)  NULL,
    paystack_webhook_secret       NVARCHAR(200)  NULL,
    flutterwave_enabled           BIT            NOT NULL CONSTRAINT DF_platform_fee_flutterwave_enabled DEFAULT 0,
    flutterwave_public_key        NVARCHAR(200)  NULL,
    flutterwave_secret_key        NVARCHAR(200)  NULL,
    flutterwave_encryption_key    NVARCHAR(200)  NULL;
