-- Custom, admin-defined RBAC roles (tenant-scoped) + user assignments.
-- Parallel to the built-in users.user_role scalar; surfaced to OIDC as a `roles` claim.

CREATE TABLE roles (
    id          VARCHAR(36)  NOT NULL,
    tenant_id   VARCHAR(36)  NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    slug         VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description  VARCHAR(512),
    -- Newline-separated free-form permission strings (Gateway-internal use).
    permissions TEXT         NOT NULL DEFAULT '',
    created_at  BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, slug)
);

CREATE TABLE user_roles (
    tenant_id VARCHAR(36) NOT NULL,
    user_id   VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id   VARCHAR(36) NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role ON user_roles (tenant_id, role_id);
