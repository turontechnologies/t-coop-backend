-- Auto-generated ID format for co-operatives (platform-wide, super admin controlled) and for
-- members within each co-op (per-co-op, that co-op's own admin controls). Existing real IDs
-- (COOP-0001, coop-0002, MB-001, AD-001, ...) were manually typed with no enforced consistency —
-- these defaults match the convention already in use, but the whole point of this feature is
-- computing the NEXT id from the format rather than trusting free text going forward.
ALTER TABLE platform_fee_settings ADD
    coop_id_prefix NVARCHAR(20) NOT NULL CONSTRAINT DF_platform_fee_settings_coop_id_prefix DEFAULT 'COOP',
    coop_id_padding INT NOT NULL CONSTRAINT DF_platform_fee_settings_coop_id_padding DEFAULT 4;

ALTER TABLE cooperatives ADD
    member_id_prefix NVARCHAR(20) NOT NULL CONSTRAINT DF_cooperatives_member_id_prefix DEFAULT 'MB',
    member_id_padding INT NOT NULL CONSTRAINT DF_cooperatives_member_id_padding DEFAULT 4;
