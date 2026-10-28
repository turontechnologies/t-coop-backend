-- Loan modes, Household Loan, and the Reports permission.
--
-- A loan is either:
--   TimesTwo — borrow up to half of your savings (your savings must be at least twice the loan).
--              Needs a guarantor.
--   Within   — borrow from your own savings, up to your savings. No guarantor. The payout is
--              deducted from your savings and every repayment's principal goes back into them.
-- Both go through the Vice President -> Treasurer -> Secretary approval chain, and both charge
-- the loan type's interest unless that type is "NoInterest".

ALTER TABLE loan_records ADD loan_mode NVARCHAR(20) NOT NULL
    CONSTRAINT DF_loan_records_loan_mode DEFAULT 'TimesTwo'
    CONSTRAINT CK_loan_records_loan_mode CHECK (loan_mode IN ('TimesTwo', 'Within'));

-- A Within loan has no guarantor.
ALTER TABLE loan_records ALTER COLUMN guarantor_name NVARCHAR(200) NULL;

-- Ledger entries that aren't tied to one savings type: "Total Savings" withdrawals (which
-- previously failed to insert here) and Within-loan debits/credits.
ALTER TABLE savings_records ALTER COLUMN savings_type_id UNIQUEIDENTIFIER NULL;

ALTER TABLE savings_records DROP CONSTRAINT CK_savings_records_method;
ALTER TABLE savings_records ADD CONSTRAINT CK_savings_records_method CHECK (method IN (
    'Paystack', 'Flutterwave', 'Manual Upload', 'Loan Disbursement', 'Loan Repayment'));

-- Household Loan for every co-op that doesn't have one (new co-ops get it on creation).
-- Defaults are a starting point each admin can edit or disable.
INSERT INTO loan_types (id, cooperative_id, name, eligibility_percent, duration_months, max_amount, repayment_interval, number_of_installments, interest_type, interest_amount, status)
SELECT NEWID(), c.id, 'Household Loan', 100, 12, 300000, 'Monthly', 12, 'Percentage', 5, 'Active'
FROM cooperatives c
WHERE NOT EXISTS (
    SELECT 1 FROM loan_types lt WHERE lt.cooperative_id = c.id AND lt.name = 'Household Loan'
);

-- Officers can read the co-op's financial report by default.
UPDATE coop_roles
SET permissions = CASE
    WHEN permissions IS NULL OR permissions = '' THEN 'Reports::read'
    ELSE permissions + ',Reports::read'
END
WHERE office IS NOT NULL
  AND (permissions IS NULL OR permissions NOT LIKE '%Reports::%');
