-- Lets the super admin (co-op ids) and each co-op's own admin (member ids) choose what kind of
-- characters the auto-generated suffix uses, not just decimal digits — NUMERIC (0-9), ALPHA
-- (A-Z), or ALPHANUMERIC (0-9 then A-Z). See CooperativeController.nextGeneratedId for the
-- base-N encode/decode this drives.
ALTER TABLE platform_fee_settings ADD
    coop_id_type NVARCHAR(20) NOT NULL CONSTRAINT DF_platform_fee_settings_coop_id_type DEFAULT 'NUMERIC'
        CONSTRAINT CK_platform_fee_settings_coop_id_type CHECK (coop_id_type IN ('NUMERIC', 'ALPHA', 'ALPHANUMERIC'));

ALTER TABLE cooperatives ADD
    member_id_type NVARCHAR(20) NOT NULL CONSTRAINT DF_cooperatives_member_id_type DEFAULT 'NUMERIC'
        CONSTRAINT CK_cooperatives_member_id_type CHECK (member_id_type IN ('NUMERIC', 'ALPHA', 'ALPHANUMERIC'));
