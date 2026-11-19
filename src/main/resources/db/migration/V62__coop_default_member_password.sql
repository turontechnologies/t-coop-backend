-- Each co-op's admin can choose the password new members start with (it goes in their welcome
-- email). Empty means the platform's default.
ALTER TABLE cooperatives ADD default_member_password NVARCHAR(100) NULL;
