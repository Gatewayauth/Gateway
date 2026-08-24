-- Multi-tenancy (row-level). Adds a tenants table and a nullable tenant_id to every
-- tenant-scoped table, backfilling existing rows to a seeded "default" tenant. A later
-- migration flips tenant_id NOT NULL and makes uniqueness composite, once the
-- application populates tenant_id on all writes.

CREATE TABLE tenants (
    id           VARCHAR(36)  PRIMARY KEY,
    slug         VARCHAR(64)  NOT NULL,
    tenant_name  VARCHAR(200) NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    created_at   BIGINT       NOT NULL
);
CREATE UNIQUE INDEX ux_tenants_slug ON tenants (slug);

INSERT INTO tenants (id, slug, tenant_name, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default', 'ACTIVE', 0);

ALTER TABLE users ADD COLUMN tenant_id VARCHAR(36);
UPDATE users SET tenant_id = '00000000-0000-0000-0000-000000000001';
CREATE INDEX ix_users_tenant ON users (tenant_id);

ALTER TABLE credentials ADD COLUMN tenant_id VARCHAR(36);
UPDATE credentials SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE sessions ADD COLUMN tenant_id VARCHAR(36);
UPDATE sessions SET tenant_id = '00000000-0000-0000-0000-000000000001';
CREATE INDEX ix_sessions_tenant ON sessions (tenant_id);

ALTER TABLE oauth_clients ADD COLUMN tenant_id VARCHAR(36);
UPDATE oauth_clients SET tenant_id = '00000000-0000-0000-0000-000000000001';
CREATE INDEX ix_oauth_clients_tenant ON oauth_clients (tenant_id);

ALTER TABLE external_identities ADD COLUMN tenant_id VARCHAR(36);
UPDATE external_identities SET tenant_id = '00000000-0000-0000-0000-000000000001';
CREATE INDEX ix_external_identities_tenant ON external_identities (tenant_id);

ALTER TABLE authorization_codes ADD COLUMN tenant_id VARCHAR(36);
UPDATE authorization_codes SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE refresh_tokens ADD COLUMN tenant_id VARCHAR(36);
UPDATE refresh_tokens SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE mfa_totp ADD COLUMN tenant_id VARCHAR(36);
UPDATE mfa_totp SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE recovery_codes ADD COLUMN tenant_id VARCHAR(36);
UPDATE recovery_codes SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE signing_keys ADD COLUMN tenant_id VARCHAR(36);
UPDATE signing_keys SET tenant_id = '00000000-0000-0000-0000-000000000001';
CREATE INDEX ix_signing_keys_tenant ON signing_keys (tenant_id);

ALTER TABLE audit_log ADD COLUMN tenant_id VARCHAR(36);
UPDATE audit_log SET tenant_id = '00000000-0000-0000-0000-000000000001';
CREATE INDEX ix_audit_tenant ON audit_log (tenant_id);

ALTER TABLE user_consents ADD COLUMN tenant_id VARCHAR(36);
UPDATE user_consents SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE account_tokens ADD COLUMN tenant_id VARCHAR(36);
UPDATE account_tokens SET tenant_id = '00000000-0000-0000-0000-000000000001';
