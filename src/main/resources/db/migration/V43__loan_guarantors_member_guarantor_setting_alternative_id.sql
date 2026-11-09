-- 1. A Times two loan now needs at least two guarantors, each accepting or declining on their own.
--    loan_records.guarantor_id/guarantor_name stay as a summary (first guarantor / all names) so
--    existing screens and reports keep working; this table is the source of truth.
CREATE TABLE loan_guarantors (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
    loan_id UNIQUEIDENTIFIER NOT NULL REFERENCES loan_records(id) ON DELETE CASCADE,
    member_id NVARCHAR(20) NOT NULL REFERENCES members(id),
    member_name NVARCHAR(200) NOT NULL,
    status NVARCHAR(10) NOT NULL CONSTRAINT DF_loan_guarantors_status DEFAULT 'Pending',
    document_url NVARCHAR(MAX) NULL,
    responded_at DATETIME2 NULL,
    created_at DATETIME2 NOT NULL CONSTRAINT DF_loan_guarantors_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_loan_guarantors_loan_member UNIQUE (loan_id, member_id),
    CONSTRAINT CK_loan_guarantors_status CHECK (status IN ('Pending', 'Accepted', 'Declined'))
);
CREATE INDEX IX_loan_guarantors_member ON loan_guarantors(member_id);
GO

-- Existing loans keep their one guarantor, with the status their loan implies.
INSERT INTO loan_guarantors (loan_id, member_id, member_name, status, document_url, responded_at)
SELECT
    id,
    guarantor_id,
    COALESCE(guarantor_name, guarantor_id),
    CASE
        WHEN guarantor_accepted_at IS NOT NULL THEN 'Accepted'
        WHEN status = 'Awaiting Guarantor' THEN 'Pending'
        WHEN status = 'Rejected' AND rejection_reason LIKE 'Guarantor declined%' THEN 'Declined'
        ELSE 'Accepted'
    END,
    guarantor_document_url,
    guarantor_accepted_at
FROM loan_records
WHERE guarantor_id IS NOT NULL;
GO

-- 2. Each co-op decides whether a new member must name guarantors (min_guarantors of them).
--    Off by default: guarantors are optional when adding a member.
ALTER TABLE cooperatives ADD require_member_guarantors BIT NOT NULL
    CONSTRAINT DF_cooperatives_require_member_guarantors DEFAULT 0;
GO

-- 3. A typed-in alternative ID for members and the admin (e.g. a staff number or an old register
--    number) — optional, unique within a co-op.
ALTER TABLE members ADD alternative_id NVARCHAR(50) NULL;
GO
CREATE UNIQUE INDEX UX_members_cooperative_alternative_id
    ON members(cooperative_id, alternative_id)
    WHERE alternative_id IS NOT NULL;
