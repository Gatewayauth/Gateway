-- Now that the application populates tenant_id on every write, enforce it and make
-- the natural keys unique per-tenant (so two tenants can share an email / external id).

ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE credentials ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE sessions ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE oauth_clients ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE external_identities ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE authorization_codes ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE mfa_totp ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE recovery_codes ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE signing_keys ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE audit_log ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE user_consents ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE account_tokens ALTER COLUMN tenant_id SET NOT NULL;

-- Email is unique per tenant, not globally.
DROP INDEX ux_users_email;
CREATE UNIQUE INDEX ux_users_tenant_email ON users (tenant_id, email);

-- An external (provider, subject) maps to at most one user per tenant.
DROP INDEX ux_external_provider_subject;
CREATE UNIQUE INDEX ux_external_tenant_provider_subject ON external_identities (tenant_id, provider, subject);
