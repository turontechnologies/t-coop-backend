-- V15 auto-seeded a Basic/Advanced/Premium savings type for every real co-op that didn't
-- already have one, mirroring the frontend's old hardcoded SAVINGS_TYPES catalog. That's
-- invented data no admin actually configured — reverses it here, for every co-op except
-- COOP-0001 (whose Basic/Advanced/Premium rows predate this feature entirely, seeded by hand in
-- V3 as real demo data, not by V15's backfill). CooperativeController no longer auto-seeds a new
-- co-op's savings types either — a co-op now legitimately has zero savings types until someone
-- deliberately configures one.
DELETE FROM savings_types WHERE cooperative_id <> 'COOP-0001';
