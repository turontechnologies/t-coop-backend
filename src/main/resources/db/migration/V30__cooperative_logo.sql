-- A co-op's own branding — shown on its admin/members' dashboard so they can see which
-- co-operative they belong to, uploaded from Settings -> Co-operative like an avatar.
ALTER TABLE cooperatives ADD logo_url NVARCHAR(500) NULL;
