-- Adds SMS (Termii) as a fourth Settings -> Integrations entry, alongside Paystack/Flutterwave/
-- OPay (V6/V14) — same singleton row, same super-admin-only pattern. Termii chosen as the
-- default/only provider for now: Nigeria-focused (matches this platform's existing NGN/Paystack/
-- OPay-first design), free trial credit on signup, simple REST API. sender_id is Termii's
-- registered "from" name for outgoing texts, not a secret.
ALTER TABLE platform_fee_settings ADD
    sms_enabled    BIT            NOT NULL CONSTRAINT DF_platform_fee_sms_enabled DEFAULT 0,
    sms_api_key    NVARCHAR(200)  NULL,
    sms_sender_id  NVARCHAR(20)   NULL;
