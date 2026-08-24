-- Single-use, expiring tokens for email verification and password reset.
CREATE TABLE account_tokens (
    token_hash   VARCHAR(64)  PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose      VARCHAR(32)  NOT NULL,
    expires_at   BIGINT       NOT NULL,
    consumed_at  BIGINT
);
CREATE INDEX ix_account_tokens_user ON account_tokens (user_id, purpose);
