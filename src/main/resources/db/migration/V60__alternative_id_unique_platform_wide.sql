-- An alternative ID can be used to log in, so it has to point at exactly one person on the whole
-- platform, not just within one co-op.
DROP INDEX UX_members_cooperative_alternative_id ON members;
CREATE UNIQUE INDEX UX_members_alternative_id ON members(alternative_id) WHERE alternative_id IS NOT NULL;
