-- Every co-op created before SavingsController/seedDefaultSavingsTypes existed (i.e. any real
-- co-op onboarded through POST /cooperatives before this feature shipped) has zero rows in
-- savings_types, unlike COOP-0001 which V3 seeded by hand. Backfill the same three starter
-- products for any cooperative that doesn't already have at least one — idempotent, so it's
-- safe even if this ever needs to run again.
INSERT INTO savings_types (id, cooperative_id, name, min_amount, max_amount, status)
SELECT NEWID(), c.id, 'Basic Savings', 5000, 10000, 'Active'
FROM cooperatives c
WHERE NOT EXISTS (SELECT 1 FROM savings_types st WHERE st.cooperative_id = c.id);

INSERT INTO savings_types (id, cooperative_id, name, min_amount, max_amount, status)
SELECT NEWID(), c.id, 'Advanced Savings', 50000, 100000, 'Active'
FROM cooperatives c
WHERE NOT EXISTS (
    SELECT 1 FROM savings_types st WHERE st.cooperative_id = c.id AND st.name = 'Advanced Savings'
);

INSERT INTO savings_types (id, cooperative_id, name, min_amount, max_amount, status)
SELECT NEWID(), c.id, 'Premium Savings', 500000, 1000000, 'Active'
FROM cooperatives c
WHERE NOT EXISTS (
    SELECT 1 FROM savings_types st WHERE st.cooperative_id = c.id AND st.name = 'Premium Savings'
);
