-- When a member is made the admin, their old Membership ID sleeps (inactive). The co-op remembers
-- it here, so when they later hand the admin role over they go back to that same Membership ID
-- instead of getting a new one.
ALTER TABLE cooperatives ADD admin_previous_member_id NVARCHAR(20) NULL;
GO

-- Admins who were members before this change: their old row was already put to sleep. Find it
-- (a sleeping row in the same co-op with the admin's name) so they can go back to it too.
UPDATE c SET admin_previous_member_id = (
    SELECT TOP 1 m.id FROM members m
    WHERE m.cooperative_id = c.id
      AND m.id <> c.id
      AND m.status = 'Inactive'
      AND m.email LIKE '%.retired@merged.t-coop.internal'
      AND m.first_name = a.first_name
      AND m.last_name = a.last_name
    ORDER BY m.invited_at DESC)
FROM cooperatives c
JOIN members a ON a.id = c.id;
