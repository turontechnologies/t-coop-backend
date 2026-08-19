-- Adds OPay as a third payment gateway alongside Paystack/Flutterwave (see V6/V9). OPay's
-- checkout is server-initiated (cashier/create returns a hosted cashierUrl to redirect the
-- payer to) rather than a client-side inline widget, but it's persisted the same way: on the
-- platform_fee_settings singleton row, super-admin-only, secret key never exposed to the client.
ALTER TABLE platform_fee_settings ADD
    opay_enabled     BIT            NOT NULL CONSTRAINT DF_platform_fee_opay_enabled DEFAULT 0,
    opay_public_key  NVARCHAR(200)  NULL,
    opay_secret_key  NVARCHAR(200)  NULL,
    opay_merchant_id NVARCHAR(100)  NULL;

ALTER TABLE subscription_payment_intents DROP CONSTRAINT CK_subscription_payment_intents_gateway;
ALTER TABLE subscription_payment_intents ADD
    CONSTRAINT CK_subscription_payment_intents_gateway CHECK (gateway IN ('Paystack', 'Flutterwave', 'Opay'));
