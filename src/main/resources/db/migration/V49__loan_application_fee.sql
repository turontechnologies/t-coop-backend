-- A loan application fee, set by the co-op's admin (Settings -> Loans) and paid online before
-- the loan form opens. A paid fee stays on file until it's used on an application, so closing
-- the form doesn't mean paying again.
ALTER TABLE cooperatives ADD
    loan_application_fee DECIMAL(18,2) NOT NULL CONSTRAINT DF_coop_loan_application_fee DEFAULT 0;
GO

CREATE TABLE loan_application_fees (
    reference       NVARCHAR(100)    NOT NULL PRIMARY KEY,
    cooperative_id  NVARCHAR(20)     NOT NULL REFERENCES cooperatives(id),
    member_id       NVARCHAR(20)     NOT NULL REFERENCES members(id),
    amount          DECIMAL(18,2)    NOT NULL,
    gateway         NVARCHAR(20)     NOT NULL,
    key_owner       NVARCHAR(20)     NOT NULL,
    -- Pending -> Paid -> Used (on an application), or Failed.
    status          NVARCHAR(20)     NOT NULL,
    loan_id         UNIQUEIDENTIFIER NULL,
    created_at      DATETIME2        NOT NULL CONSTRAINT DF_loan_application_fees_created_at DEFAULT SYSUTCDATETIME(),
    paid_at         DATETIME2        NULL,
    used_at         DATETIME2        NULL,
    CONSTRAINT CK_loan_application_fees_status CHECK (status IN ('Pending', 'Paid', 'Used', 'Failed'))
);
CREATE INDEX IX_loan_application_fees_member ON loan_application_fees(member_id, status);
GO

ALTER TABLE loan_records ADD
    application_fee_amount     DECIMAL(18,2)  NULL,
    application_fee_reference  NVARCHAR(100)  NULL;
GO
