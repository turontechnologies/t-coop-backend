-- The loan application fee can be one fee for every loan ("General") or set per loan type
-- ("PerType"). A fee payment remembers which loan type it was paid for, so a fee paid for one
-- type is never spent on another.
ALTER TABLE cooperatives ADD
    loan_fee_mode NVARCHAR(10) NOT NULL CONSTRAINT DF_coop_loan_fee_mode DEFAULT 'General';
GO

ALTER TABLE cooperatives ADD CONSTRAINT CK_coop_loan_fee_mode CHECK (loan_fee_mode IN ('General', 'PerType'));
GO

ALTER TABLE loan_types ADD application_fee DECIMAL(18,2) NULL;
GO

ALTER TABLE loan_application_fees ADD loan_type_id UNIQUEIDENTIFIER NULL;
GO
