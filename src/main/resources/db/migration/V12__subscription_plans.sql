-- Super admin's own editable price list for subscriptions (Payment Settings > Subscription
-- Plans) -- replaces the old hardcoded Weekly/Monthly/Quarterly/Yearly fractions-of-a-fixed-fee
-- computation with real, addable/editable/deletable rows. `duration_in_days` is the flexible
-- unit (not a fixed enum) so a plan can be any length the super admin wants, not just calendar
-- months; `label` is whatever they want to call it (still free to name one "6 Months" and mean
-- exactly that).
CREATE TABLE subscription_plans (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    type                NVARCHAR(20)     NOT NULL,
    label               NVARCHAR(50)     NOT NULL,
    duration_in_days    INT              NOT NULL,
    amount              DECIMAL(18,2)    NOT NULL,
    status              NVARCHAR(20)     NOT NULL CONSTRAINT DF_subscription_plans_status DEFAULT 'Active',
    created_at          DATETIME2        NOT NULL CONSTRAINT DF_subscription_plans_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_subscription_plans_type CHECK (type IN ('New Subscription', 'Renewal')),
    CONSTRAINT CK_subscription_plans_status CHECK (status IN ('Active', 'Inactive')),
    CONSTRAINT CK_subscription_plans_duration CHECK (duration_in_days > 0)
);

-- Seeded with exactly what the Support page already showed (a co-op's yearly fee of NGN150,000
-- sliced into Weekly/Monthly/Quarterly/Yearly) -- same numbers, now a real editable catalog
-- instead of computed fractions. Both New Subscription and Renewal start identical; the super
-- admin can diverge them from here.
INSERT INTO subscription_plans (type, label, duration_in_days, amount) VALUES
    ('New Subscription', 'Weekly',    7,   2885.00),
    ('New Subscription', 'Monthly',   30,  12500.00),
    ('New Subscription', 'Quarterly', 90,  37500.00),
    ('New Subscription', 'Yearly',    365, 150000.00),
    ('Renewal',          'Weekly',    7,   2885.00),
    ('Renewal',          'Monthly',   30,  12500.00),
    ('Renewal',          'Quarterly', 90,  37500.00),
    ('Renewal',          'Yearly',    365, 150000.00);
