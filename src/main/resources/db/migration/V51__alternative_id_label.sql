-- What a co-op calls the alternative ID (e.g. "Staff No.", "Old Register No."). Null means the
-- default, "Alternative ID".
ALTER TABLE cooperatives ADD alternative_id_label NVARCHAR(40) NULL;
GO
