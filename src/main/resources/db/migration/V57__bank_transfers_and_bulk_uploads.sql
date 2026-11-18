-- Money that moves outside the platform's payment gateway, and bulk uploads.
--
-- 1. A loan can be marked as paid out by a bank transfer the co-op already made itself
--    (disbursement_method 'Transfer'), with the transfer receipt and reference kept in the
--    existing cheque_url / cheque_number columns.
-- 2. A borrower who repaid by bank transfer submits it for the co-op to confirm
--    (loan_repayment_claims); once confirmed it becomes ordinary loan_repayments rows with
--    method 'Bank Transfer', so balances and schedules count it like any other repayment.
-- 3. Bulk uploads of savings and loan repayments are recorded with method 'Bulk Upload'.
-- 4. A declined withdrawal can carry the reason, which the member is told.

ALTER TABLE loan_records DROP CONSTRAINT CK_loan_records_disbursement_method;
ALTER TABLE loan_records ADD CONSTRAINT CK_loan_records_disbursement_method
    CHECK (disbursement_method IS NULL OR disbursement_method IN ('Online', 'Cheque', 'Transfer', 'Import'));

ALTER TABLE loan_repayments DROP CONSTRAINT CK_loan_repayments_method;
ALTER TABLE loan_repayments ADD CONSTRAINT CK_loan_repayments_method
    CHECK (method IN ('Paystack', 'Flutterwave', 'Manual Upload', 'Bank Transfer', 'Bulk Upload'));

ALTER TABLE savings_records DROP CONSTRAINT CK_savings_records_method;
ALTER TABLE savings_records ADD CONSTRAINT CK_savings_records_method
    CHECK (method IN ('Paystack', 'Flutterwave', 'Manual Upload', 'Bulk Upload', 'Loan Disbursement', 'Loan Repayment'));

ALTER TABLE savings_requests ADD decline_reason NVARCHAR(500) NULL;

CREATE TABLE loan_repayment_claims (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    loan_id             UNIQUEIDENTIFIER NOT NULL REFERENCES loan_records(id),
    cooperative_id      NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id           NVARCHAR(20)     NOT NULL REFERENCES members(id),
    installments        INT              NOT NULL,
    amount              DECIMAL(18,2)    NOT NULL,
    receipt_url         NVARCHAR(500)    NOT NULL,
    transfer_reference  NVARCHAR(100)    NULL,
    paid_on             DATE             NOT NULL,
    note                NVARCHAR(500)    NULL,
    status              NVARCHAR(20)     NOT NULL,
    submitted_at        DATETIME2        NOT NULL,
    decided_at          DATETIME2        NULL,
    decided_by_name     NVARCHAR(200)    NULL,
    rejection_reason    NVARCHAR(500)    NULL,
    CONSTRAINT CK_loan_repayment_claims_status CHECK (status IN ('Pending', 'Confirmed', 'Rejected')),
    CONSTRAINT CK_loan_repayment_claims_installments CHECK (installments >= 1)
);
CREATE INDEX IX_loan_repayment_claims_loan_id ON loan_repayment_claims(loan_id);
CREATE INDEX IX_loan_repayment_claims_coop_status ON loan_repayment_claims(cooperative_id, status);
