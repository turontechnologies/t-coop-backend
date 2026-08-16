-- A co-op admin's self-service subscription payment (Paystack/Flutterwave) goes through two
-- steps: initialize (we decide the exact amount for the chosen cycle and hand back a reference)
-- then confirm (we verify that exact reference really was paid, server-side, against the real
-- gateway API, before ever creating a subscription_payments row). This table is the bridge
-- between those two steps -- it's what "confirm" looks up by reference, so the amount actually
-- credited always traces back to what we ourselves decided to charge, never to a client-supplied
-- number.
CREATE TABLE subscription_payment_intents (
    reference       NVARCHAR(100)    NOT NULL PRIMARY KEY,
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    amount          DECIMAL(18,2)    NOT NULL,
    cycle           NVARCHAR(20)     NOT NULL,
    gateway         NVARCHAR(20)     NOT NULL,
    status          NVARCHAR(20)     NOT NULL CONSTRAINT DF_subscription_payment_intents_status DEFAULT 'Pending',
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_subscription_payment_intents_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_subscription_payment_intents_cycle
        CHECK (cycle IN ('Weekly', 'Monthly', 'Quarterly', 'Yearly')),
    CONSTRAINT CK_subscription_payment_intents_gateway CHECK (gateway IN ('Paystack', 'Flutterwave')),
    CONSTRAINT CK_subscription_payment_intents_status CHECK (status IN ('Pending', 'Confirmed', 'Failed'))
);
CREATE INDEX IX_subscription_payment_intents_cooperative_id
    ON subscription_payment_intents(cooperative_id);
