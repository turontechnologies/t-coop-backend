-- New-member guarantor rules. When a co-op requires guarantors for new members, the admin also
-- sets where they may come from:
--   Any      – anyone (members of the co-op or outsiders)
--   CoopOnly – members of this co-operative only
--   Mix      – at least member_guarantors_from_coop members and at least
--              member_guarantors_from_outside outsiders; the rest can be either
-- min_guarantors (already there) stays the minimum total.
ALTER TABLE cooperatives ADD member_guarantor_source NVARCHAR(10) NOT NULL
    CONSTRAINT DF_cooperatives_member_guarantor_source DEFAULT 'Any';
ALTER TABLE cooperatives ADD member_guarantors_from_coop INT NOT NULL
    CONSTRAINT DF_cooperatives_member_guarantors_from_coop DEFAULT 0;
ALTER TABLE cooperatives ADD member_guarantors_from_outside INT NOT NULL
    CONSTRAINT DF_cooperatives_member_guarantors_from_outside DEFAULT 0;
GO

ALTER TABLE cooperatives ADD CONSTRAINT CK_cooperatives_member_guarantor_source
    CHECK (member_guarantor_source IN ('Any', 'CoopOnly', 'Mix'));
GO

-- Each guarantor named on Add/Edit Member is a row again: a co-op member (guarantor_member_id set)
-- or an outsider (name + phone, email optional). 'Named' = recorded by the admin, no email invite.
ALTER TABLE member_guarantors ADD guarantor_member_id NVARCHAR(20) NULL;
ALTER TABLE member_guarantors ALTER COLUMN email NVARCHAR(255) NULL;
ALTER TABLE member_guarantors DROP CONSTRAINT CK_member_guarantors_status;
GO

ALTER TABLE member_guarantors ADD CONSTRAINT CK_member_guarantors_status
    CHECK (status IN ('Pending', 'Accepted', 'Declined', 'Named'));
GO
