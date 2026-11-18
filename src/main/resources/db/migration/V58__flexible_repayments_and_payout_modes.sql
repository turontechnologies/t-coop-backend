-- 1. Loan repayments can be any amount, not only whole installments. A loan keeps the running
--    total repaid (amount_repaid); repayments_made becomes "installments fully covered" by it,
--    and the loan completes when amount_repaid reaches total_repayment.
ALTER TABLE loan_records ADD amount_repaid DECIMAL(18,2) NULL;
GO

UPDATE l SET amount_repaid =
    CASE
        WHEN l.status = 'Completed' THEN l.total_repayment
        WHEN ISNULL(r.paid, 0) > 0 THEN CASE WHEN r.paid > l.total_repayment THEN l.total_repayment ELSE r.paid END
        WHEN l.repayments_made > 0 THEN
            CASE WHEN l.monthly_repayment * l.repayments_made > l.total_repayment
                 THEN l.total_repayment ELSE l.monthly_repayment * l.repayments_made END
        ELSE 0
    END
FROM loan_records l
OUTER APPLY (SELECT SUM(amount) AS paid FROM loan_repayments WHERE loan_id = l.id) r
WHERE l.status IN ('Active', 'Completed');

-- A bank-transfer claim is now an amount; "installments" is just how many it fully clears.
ALTER TABLE loan_repayment_claims DROP CONSTRAINT CK_loan_repayment_claims_installments;

-- 2. Withdrawals (and loan payouts) can be paid out however the co-op actually paid:
--    online through the platform, a bank transfer it already made, a cheque, or cash.
ALTER TABLE savings_requests ADD payout_method NVARCHAR(20) NULL;
ALTER TABLE savings_requests ADD payout_reference NVARCHAR(100) NULL;
ALTER TABLE savings_requests ADD payout_receipt_url NVARCHAR(500) NULL;

ALTER TABLE savings_records DROP CONSTRAINT CK_savings_records_method;
ALTER TABLE savings_records ADD CONSTRAINT CK_savings_records_method
    CHECK (method IN ('Paystack', 'Flutterwave', 'Manual Upload', 'Bulk Upload', 'Bank Transfer', 'Cheque', 'Cash',
                      'Loan Disbursement', 'Loan Repayment'));

ALTER TABLE loan_records DROP CONSTRAINT CK_loan_records_disbursement_method;
ALTER TABLE loan_records ADD CONSTRAINT CK_loan_records_disbursement_method
    CHECK (disbursement_method IS NULL OR disbursement_method IN ('Online', 'Cheque', 'Transfer', 'Cash', 'Import'));
