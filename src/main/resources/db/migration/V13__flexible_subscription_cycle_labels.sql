-- The Weekly/Monthly/Quarterly/Yearly enum is retired -- a "cycle"/"subscription_cycle" value is
-- now whatever label a subscription_plans row has, which the super admin can freely add/edit/
-- delete (V12), so these columns can no longer be constrained to four fixed strings.
ALTER TABLE cooperatives DROP CONSTRAINT CK_cooperatives_subscription_cycle;
ALTER TABLE subscription_payments DROP CONSTRAINT CK_subscription_payments_cycle;
ALTER TABLE subscription_payment_intents DROP CONSTRAINT CK_subscription_payment_intents_cycle;

-- The intent now carries the plan's exact duration forward from initialize to confirm, instead
-- of confirm having to re-derive "how many days does this label mean" from free text.
ALTER TABLE subscription_payment_intents ADD
    duration_in_days  INT  NULL;
