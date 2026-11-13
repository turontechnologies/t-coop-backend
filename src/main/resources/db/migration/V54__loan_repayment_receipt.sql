-- A repayment recorded by hand (cash or bank transfer received offline) now carries proof:
-- the uploaded receipt, plus who recorded it and an optional note.
ALTER TABLE loan_repayments ADD receipt_url NVARCHAR(MAX) NULL;
ALTER TABLE loan_repayments ADD recorded_by_name NVARCHAR(200) NULL;
ALTER TABLE loan_repayments ADD note NVARCHAR(500) NULL;
GO
