-- Backfilled using each co-op's current expiry as a reasonable approximation -- accurate for the
-- latest payment per co-op; historical rows before that have no better source of truth available
-- now. See V10 for why this couldn't be combined into the same script as the ALTER TABLE.
UPDATE p SET p.resulting_expires_at = c.subscription_expires_at
FROM subscription_payments p
JOIN cooperatives c ON p.cooperative_id = c.id
WHERE p.resulting_expires_at IS NULL;
