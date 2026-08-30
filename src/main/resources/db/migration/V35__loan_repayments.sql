-- A borrower's own loan repayment (Paystack) goes through the same initialize-then-confirm
-- pattern as a savings deposit — see savings_payment_intents. A manual (admin-recorded) repayment
-- skips this table entirely and writes straight to loan_repayments.
CREATE TABLE loan_payment_intents (
    reference           NVARCHAR(100)    NOT NULL PRIMARY KEY,
    loan_id             UNIQUEIDENTIFIER NOT NULL REFERENCES loan_records(id),
    cooperative_id      NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id           NVARCHAR(20)     NOT NULL REFERENCES members(id),
    installment_number  INT              NOT NULL,
    amount              DECIMAL(18,2)    NOT NULL,
    status              NVARCHAR(20)     NOT NULL CONSTRAINT DF_loan_payment_intents_status DEFAULT 'Pending',
    created_at          DATETIME2        NOT NULL CONSTRAINT DF_loan_payment_intents_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loan_payment_intents_status CHECK (status IN ('Pending', 'Confirmed', 'Failed'))
);
CREATE INDEX IX_loan_payment_intents_loan_id ON loan_payment_intents(loan_id);

-- One real installment payment against a loan — either the borrower's own Paystack checkout or
-- an admin's manual entry after receiving payment offline (cash/bank transfer), same "Paystack vs
-- Manual Upload" split as savings_records. Each row always pays exactly one fixed installment
-- (loan_records.monthly_repayment, or the exact remainder on the final installment) — this app
-- doesn't support partial/arbitrary-amount repayments.
CREATE TABLE loan_repayments (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    loan_id             UNIQUEIDENTIFIER NOT NULL REFERENCES loan_records(id),
    cooperative_id      NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id           NVARCHAR(20)     NOT NULL REFERENCES members(id),
    installment_number  INT              NOT NULL,
    amount              DECIMAL(18,2)    NOT NULL,
    method              NVARCHAR(20)     NOT NULL,
    transaction_id      NVARCHAR(100)    NOT NULL,
    repayment_date      DATE             NOT NULL,
    status              NVARCHAR(20)     NOT NULL,
    created_at          DATETIME2        NOT NULL CONSTRAINT DF_loan_repayments_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_loan_repayments_method CHECK (method IN ('Paystack', 'Manual Upload')),
    CONSTRAINT CK_loan_repayments_status CHECK (status IN ('Success'))
);
CREATE INDEX IX_loan_repayments_loan_id ON loan_repayments(loan_id);
CREATE INDEX IX_loan_repayments_cooperative_id ON loan_repayments(cooperative_id);
