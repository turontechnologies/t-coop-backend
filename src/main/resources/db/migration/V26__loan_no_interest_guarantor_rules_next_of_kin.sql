-- Loan types: a co-op can now offer a genuinely interest-free loan — interest_amount becomes
-- optional (NULL/0 when interest_type is NoInterest), and the type itself allows a third option.
ALTER TABLE loan_types DROP CONSTRAINT CK_loan_types_interest_type;
ALTER TABLE loan_types ALTER COLUMN interest_amount DECIMAL(18,2) NULL;
ALTER TABLE loan_types ADD CONSTRAINT CK_loan_types_interest_type
    CHECK (interest_type IN ('Percentage', 'Fixed', 'NoInterest'));

-- Each co-op's own admin sets how many guarantors a new member needs (at least 2, enforced in
-- CooperativeController.addMember alongside the "at least one must be an existing member of this
-- co-op" rule — guarantors themselves stay a comma-separated list in the existing `guarantor`
-- column, no new table needed for that part).
ALTER TABLE cooperatives ADD
    min_guarantors INT NOT NULL CONSTRAINT DF_cooperatives_min_guarantors DEFAULT 2;

-- Next of kin — simple two-field contact, same spirit as guarantor (a name plus how to reach
-- them), not a separate related entity.
ALTER TABLE members ADD
    next_of_kin_name NVARCHAR(200) NULL,
    next_of_kin_phone NVARCHAR(30) NULL;
