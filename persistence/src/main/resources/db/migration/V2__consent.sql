-- Recorded user→client consents (granted OAuth scopes per relying party).
CREATE TABLE user_consents (
    user_id     VARCHAR(36)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_id   VARCHAR(200) NOT NULL REFERENCES oauth_clients (client_id) ON DELETE CASCADE,
    scopes      TEXT         NOT NULL DEFAULT '',
    granted_at  BIGINT       NOT NULL,
    PRIMARY KEY (user_id, client_id)
);
