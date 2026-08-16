-- The receipt for any subscription payment -- immediately after paying, or re-downloaded weeks
-- later from history -- needs to show what that specific payment bought (its resulting expiry
-- date), not just cooperatives.subscription_expires_at, which only ever reflects the LATEST
-- payment. Backfill lives in V11 -- SQL Server won't resolve a column added earlier in the same
-- batch/script, so the ALTER and the UPDATE that reads the new column must be separate migrations.
ALTER TABLE subscription_payments ADD
    resulting_expires_at  DATE  NULL;
