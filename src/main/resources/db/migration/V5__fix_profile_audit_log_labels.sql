-- Historical rows written before ProfileController was fixed to use module
-- "Settings" / action "Update" (matching the frontend's fixed AuditModule/
-- AuditAction enums, src/lib/audit-log-data.ts) still have the old, invalid
-- "Profile" / "Update Profile" values — which have no icon mapping on the
-- frontend and crash the Logs tab. Corrects existing data to match.

UPDATE audit_log
SET module = 'Settings',
    action = 'Update',
    resource = 'Profile'
WHERE module = 'Profile' AND action = 'Update Profile';
