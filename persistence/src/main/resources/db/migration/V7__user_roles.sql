-- User-bound admin: per-tenant role + global super-admin flag.
-- Replaces the shared X-Admin-Token model. Existing users default to USER.
-- `user_role` (not `role`, a SQL keyword) so identifiers stay unquoted/consistent.
ALTER TABLE users ADD COLUMN user_role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;
