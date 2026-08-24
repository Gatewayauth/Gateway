-- Gateway initial schema.
-- IDs are stored as VARCHAR(36) (UUID text) for portability across Postgres/H2.
-- Timestamps are epoch millis (BIGINT). Secrets are stored only as hashes.

CREATE TABLE users (
    id                VARCHAR(36)  PRIMARY KEY,
    email             VARCHAR(320) NOT NULL,
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    display_name      VARCHAR(200),
    status            VARCHAR(32)  NOT NULL,
    mfa_required      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        BIGINT       NOT NULL,
    updated_at        BIGINT       NOT NULL
);
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE credentials (
    user_id           VARCHAR(36)  PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    password_hash     TEXT         NOT NULL,
    updated_at        BIGINT       NOT NULL
);

CREATE TABLE sessions (
    id                VARCHAR(36)  PRIMARY KEY,
    user_id           VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash        VARCHAR(64)  NOT NULL,
    amr               TEXT         NOT NULL DEFAULT '',
    created_at        BIGINT       NOT NULL,
    last_seen_at      BIGINT       NOT NULL,
    expires_at        BIGINT       NOT NULL,
    revoked_at        BIGINT,
    ip                VARCHAR(64),
    user_agent        VARCHAR(512)
);
CREATE UNIQUE INDEX ux_sessions_token_hash ON sessions (token_hash);
CREATE INDEX ix_sessions_user_id ON sessions (user_id);

CREATE TABLE oauth_clients (
    client_id         VARCHAR(200) PRIMARY KEY,
    client_name       VARCHAR(200) NOT NULL,
    is_public         BOOLEAN      NOT NULL,
    secret_hash       VARCHAR(128),
    redirect_uris     TEXT         NOT NULL DEFAULT '',
    allowed_scopes    TEXT         NOT NULL DEFAULT '',
    grant_types       TEXT         NOT NULL DEFAULT '',
    require_pkce      BOOLEAN      NOT NULL DEFAULT TRUE,
    require_consent   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        BIGINT       NOT NULL
);

-- External identities (Google/GitHub/Discord) linked to a local user.
CREATE TABLE external_identities (
    id                VARCHAR(36)  PRIMARY KEY,
    user_id           VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          VARCHAR(64)  NOT NULL,
    subject           VARCHAR(255) NOT NULL,
    email             VARCHAR(320),
    created_at        BIGINT       NOT NULL
);
CREATE UNIQUE INDEX ux_external_provider_subject ON external_identities (provider, subject);

-- OIDC authorization codes (single-use). Only the SHA-256 hash is stored.
CREATE TABLE authorization_codes (
    code_hash         VARCHAR(64)  PRIMARY KEY,
    client_id         VARCHAR(200) NOT NULL REFERENCES oauth_clients (client_id) ON DELETE CASCADE,
    user_id           VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    redirect_uri      TEXT         NOT NULL,
    scopes            TEXT         NOT NULL DEFAULT '',
    nonce             VARCHAR(255),
    code_challenge    VARCHAR(255),
    code_challenge_method VARCHAR(10),
    auth_time         BIGINT       NOT NULL,
    expires_at        BIGINT       NOT NULL,
    consumed_at       BIGINT
);

-- Refresh tokens with rotation + reuse detection (family/prev linkage).
CREATE TABLE refresh_tokens (
    token_hash        VARCHAR(64)  PRIMARY KEY,
    family_id         VARCHAR(64)  NOT NULL,
    client_id         VARCHAR(200) NOT NULL REFERENCES oauth_clients (client_id) ON DELETE CASCADE,
    user_id           VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scopes            TEXT         NOT NULL DEFAULT '',
    issued_at         BIGINT       NOT NULL,
    expires_at        BIGINT       NOT NULL,
    rotated_at        BIGINT,
    revoked_at        BIGINT
);
CREATE INDEX ix_refresh_family ON refresh_tokens (family_id);

-- TOTP MFA enrollment (secret encrypted at rest by the application layer).
CREATE TABLE mfa_totp (
    user_id           VARCHAR(36)  PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret_enc        TEXT         NOT NULL,
    confirmed_at      BIGINT,
    created_at        BIGINT       NOT NULL
);

-- One-time recovery codes (stored hashed, single-use).
CREATE TABLE recovery_codes (
    id                VARCHAR(36)  PRIMARY KEY,
    user_id           VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash         VARCHAR(64)  NOT NULL,
    used_at           BIGINT,
    created_at        BIGINT       NOT NULL
);
CREATE INDEX ix_recovery_user ON recovery_codes (user_id);

-- JWT signing keys (rotated; prior keys retained in JWKS until expiry).
CREATE TABLE signing_keys (
    kid               VARCHAR(64)  PRIMARY KEY,
    algorithm         VARCHAR(16)  NOT NULL,
    public_jwk        TEXT         NOT NULL,
    private_key_enc   TEXT         NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        BIGINT       NOT NULL,
    expires_at        BIGINT
);

-- Append-only security audit log.
CREATE TABLE audit_log (
    id                VARCHAR(36)  PRIMARY KEY,
    event_at          BIGINT       NOT NULL,
    actor_user_id     VARCHAR(36),
    event_type        VARCHAR(64)  NOT NULL,
    ip                VARCHAR(64),
    user_agent        VARCHAR(512),
    detail            TEXT
);
CREATE INDEX ix_audit_at ON audit_log (event_at);
CREATE INDEX ix_audit_actor ON audit_log (actor_user_id);
