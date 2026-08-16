-- A co-operative's platform subscription: a billing cycle (Weekly/Monthly/Quarterly/Yearly)
-- and the date the current period expires. Both start NULL — a co-op that has never had a
-- subscription payment recorded is treated the same as one whose subscription has lapsed
-- (see SubscriptionGateFilter): no co-op can act on the platform until the super admin
-- records its first payment.
ALTER TABLE cooperatives ADD
    subscription_cycle       NVARCHAR(20) NULL
        CONSTRAINT CK_cooperatives_subscription_cycle
        CHECK (subscription_cycle IN ('Weekly', 'Monthly', 'Quarterly', 'Yearly')),
    subscription_expires_at  DATE         NULL;

-- `type` records whether a payment was the co-op's very first ("New Subscription") or a
-- later renewal — set server-side from whether the co-op already had an expiry date, never
-- client-supplied, so it can't drift from reality. `cycle` is the billing cycle this specific
-- payment bought (a co-op can change cycle from one renewal to the next).
ALTER TABLE subscription_payments ADD
    type   NVARCHAR(20) NOT NULL
        CONSTRAINT DF_subscription_payments_type DEFAULT 'New Subscription'
        CONSTRAINT CK_subscription_payments_type CHECK (type IN ('New Subscription', 'Renewal')),
    cycle  NVARCHAR(20) NOT NULL
        CONSTRAINT DF_subscription_payments_cycle DEFAULT 'Yearly'
        CONSTRAINT CK_subscription_payments_cycle CHECK (cycle IN ('Weekly', 'Monthly', 'Quarterly', 'Yearly'));
