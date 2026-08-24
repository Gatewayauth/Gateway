-- Human-readable actor for audit events without a user id (e.g. token-authed admin).
ALTER TABLE audit_log ADD COLUMN actor_label VARCHAR(64);
