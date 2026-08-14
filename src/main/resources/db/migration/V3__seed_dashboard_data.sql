-- Dashboard demo data for COOP-0001 — savings/loan type catalogs plus a
-- handful of real savings and loan records for AD-0001 (admin) and
-- MB-0001 (member), so GET /api/v1/dashboard/summary has real numbers to
-- aggregate instead of returning zeros. Values roughly mirror the
-- frontend's static SAVINGS_TYPES/LOAN_TYPES (t-coop-app/src/lib/*-data.ts)
-- for continuity between the mock and the real backend.

INSERT INTO savings_types (id, cooperative_id, name, min_amount, max_amount, status)
VALUES
    (NEWID(), 'COOP-0001', 'Basic Savings',    5000,   10000,    'Active'),
    (NEWID(), 'COOP-0001', 'Advanced Savings', 50000,  100000,   'Active'),
    (NEWID(), 'COOP-0001', 'Premium Savings',  500000, 1000000,  'Active');

INSERT INTO loan_types (id, cooperative_id, name, eligibility_percent, duration_months, max_amount, repayment_interval, number_of_installments, interest_type, interest_amount, status)
VALUES
    (NEWID(), 'COOP-0001', 'Emergency Loan', 300, 3,  50000,  'Monthly', 3,  'Percentage', 5,  'Active'),
    (NEWID(), 'COOP-0001', 'Education Loan', 200, 6,  200000, 'Monthly', 6,  'Percentage', 7,  'Active'),
    (NEWID(), 'COOP-0001', 'Business Loan',  100, 12, 500000, 'Monthly', 12, 'Percentage', 10, 'Active');

-- Savings records — member MB-0001
INSERT INTO savings_records (id, cooperative_id, member_id, savings_type_id, amount, balance_after, method, transaction_id, record_date, status)
SELECT NEWID(), 'COOP-0001', 'MB-0001', id, 85000, 85000, 'Manual Upload', 'TXN-SEED-001', '2026-06-09', 'Success'
FROM savings_types WHERE cooperative_id = 'COOP-0001' AND name = 'Basic Savings';

INSERT INTO savings_records (id, cooperative_id, member_id, savings_type_id, amount, balance_after, method, transaction_id, record_date, status)
SELECT NEWID(), 'COOP-0001', 'MB-0001', id, 90000, 90000, 'Manual Upload', 'TXN-SEED-002', '2026-07-09', 'Success'
FROM savings_types WHERE cooperative_id = 'COOP-0001' AND name = 'Advanced Savings';

INSERT INTO savings_records (id, cooperative_id, member_id, savings_type_id, amount, balance_after, method, transaction_id, record_date, status)
SELECT NEWID(), 'COOP-0001', 'MB-0001', id, 235000, 235000, 'Paystack', 'TXN-SEED-003', '2026-08-01', 'Success'
FROM savings_types WHERE cooperative_id = 'COOP-0001' AND name = 'Premium Savings';

-- Savings records — admin AD-0001 (admins also have their own personal savings)
INSERT INTO savings_records (id, cooperative_id, member_id, savings_type_id, amount, balance_after, method, transaction_id, record_date, status)
SELECT NEWID(), 'COOP-0001', 'AD-0001', id, 120000, 120000, 'Manual Upload', 'TXN-SEED-004', '2026-06-18', 'Success'
FROM savings_types WHERE cooperative_id = 'COOP-0001' AND name = 'Basic Savings';

-- Loan records — member MB-0001
INSERT INTO loan_records (id, cooperative_id, member_id, loan_type_id, amount, interest_rate, duration_months, number_of_repayments, monthly_repayment, total_repayment, guarantor_name, loan_date, status, repayments_made)
SELECT NEWID(), 'COOP-0001', 'MB-0001', id, 40000, 5, 3, 3, 14000, 42000, 'Chidinma Eze', '2026-03-01', 'Completed', 3
FROM loan_types WHERE cooperative_id = 'COOP-0001' AND name = 'Emergency Loan';

INSERT INTO loan_records (id, cooperative_id, member_id, loan_type_id, amount, interest_rate, duration_months, number_of_repayments, monthly_repayment, total_repayment, guarantor_name, loan_date, status, repayments_made)
SELECT NEWID(), 'COOP-0001', 'MB-0001', id, 150000, 7, 6, 6, 26750, 160500, 'Falola Mayowa', '2026-07-01', 'Active', 1
FROM loan_types WHERE cooperative_id = 'COOP-0001' AND name = 'Education Loan';

-- Loan records — admin AD-0001
INSERT INTO loan_records (id, cooperative_id, member_id, loan_type_id, amount, interest_rate, duration_months, number_of_repayments, monthly_repayment, total_repayment, guarantor_name, loan_date, status, repayments_made)
SELECT NEWID(), 'COOP-0001', 'AD-0001', id, 30000, 5, 3, 3, 10500, 31500, 'Tunde Bakare', '2026-06-01', 'Active', 1
FROM loan_types WHERE cooperative_id = 'COOP-0001' AND name = 'Emergency Loan';
