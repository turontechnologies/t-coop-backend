-- Seeds the same three loan products (Emergency/Education/Business Loan, matching COOP-0001's
-- own V3-seeded values) for every real co-op that doesn't already have any loan types. Unlike
-- savings_types (V15/V16 — see that migration's note on why auto-seeding was reversed there),
-- this is a deliberate, explicit, one-time seed for loan types specifically, requested directly
-- rather than invented on the fly — CooperativeController does NOT auto-seed loan types on new
-- co-op creation, so a co-op onboarded after this migration runs still starts with none until
-- someone seeds/configures them explicitly.
INSERT INTO loan_types (id, cooperative_id, name, eligibility_percent, duration_months, max_amount, repayment_interval, number_of_installments, interest_type, interest_amount, status)
SELECT NEWID(), c.id, 'Emergency Loan', 300, 3, 50000, 'Monthly', 3, 'Percentage', 5, 'Active'
FROM cooperatives c
WHERE NOT EXISTS (SELECT 1 FROM loan_types lt WHERE lt.cooperative_id = c.id);

INSERT INTO loan_types (id, cooperative_id, name, eligibility_percent, duration_months, max_amount, repayment_interval, number_of_installments, interest_type, interest_amount, status)
SELECT NEWID(), c.id, 'Education Loan', 200, 6, 200000, 'Monthly', 6, 'Percentage', 7, 'Active'
FROM cooperatives c
WHERE NOT EXISTS (
    SELECT 1 FROM loan_types lt WHERE lt.cooperative_id = c.id AND lt.name = 'Education Loan'
);

INSERT INTO loan_types (id, cooperative_id, name, eligibility_percent, duration_months, max_amount, repayment_interval, number_of_installments, interest_type, interest_amount, status)
SELECT NEWID(), c.id, 'Business Loan', 100, 12, 500000, 'Monthly', 12, 'Percentage', 10, 'Active'
FROM cooperatives c
WHERE NOT EXISTS (
    SELECT 1 FROM loan_types lt WHERE lt.cooperative_id = c.id AND lt.name = 'Business Loan'
);
