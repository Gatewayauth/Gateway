package io.gateway.domain.model

/**
 * Authorization role a [User] holds within their tenant.
 * - [USER]: no admin access (default).
 * - [ADMIN]: may use the per-tenant admin API.
 * - [OWNER]: admin, plus may grant/revoke other users' roles.
 *
 * Cross-tenant provisioning is gated separately by [User.superAdmin].
 */
enum class Role {
    USER,
    ADMIN,
    OWNER,
}
