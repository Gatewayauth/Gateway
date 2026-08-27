-- Per-client access gate: role slugs a user must hold (at least one of) to be
-- authorized against the client. Empty = no gate. Space-separated, mirroring
-- allowed_scopes.
ALTER TABLE oauth_clients ADD COLUMN required_roles TEXT NOT NULL DEFAULT '';
