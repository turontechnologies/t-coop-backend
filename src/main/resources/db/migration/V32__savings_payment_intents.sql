-- Mirrors V9__subscription_payment_intents.sql, for the same reason: a member's self-service
-- savings deposit goes through initialize (we decide the exact amount, hand back a reference)
-- then confirm (verify that reference really was paid, server-side, before crediting anything).
CREATE TABLE savings_payment_intents (
    reference       NVARCHAR(100)    NOT NULL PRIMARY KEY,
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id       NVARCHAR(20)     NOT NULL REFERENCES members(id),
    savings_type_id UNIQUEIDENTIFIER NOT NULL REFERENCES savings_types(id),
    amount          DECIMAL(18,2)    NOT NULL,
    status          NVARCHAR(20)     NOT NULL CONSTRAINT DF_savings_payment_intents_status DEFAULT 'Pending',
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_savings_payment_intents_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_savings_payment_intents_status CHECK (status IN ('Pending', 'Confirmed', 'Failed'))
);
CREATE INDEX IX_savings_payment_intents_cooperative_id ON savings_payment_intents(cooperative_id);
CREATE INDEX IX_savings_payment_intents_member_id ON savings_payment_intents(member_id);
