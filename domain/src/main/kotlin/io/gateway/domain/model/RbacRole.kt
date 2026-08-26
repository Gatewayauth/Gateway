package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A custom, admin-defined authorization role scoped to a tenant. Distinct from the
 * built-in [Role] enum (which gates Gateway's own admin UI).
 *
 * - [slug] is immutable and stable — it is what surfaces to relying parties in the
 *   `roles` OIDC claim, so renaming the human-facing [name] never breaks RP mappings.
 * - [permissions] are free-form strings for Gateway-internal authorization; they are
 *   not emitted to tokens.
 */
data class RbacRole(
    val id: RoleId,
    val tenantId: TenantId,
    val slug: String,
    val name: String,
    val description: String?,
    val permissions: Set<String>,
    val createdAt: Instant,
)

/** True if any of the given roles grants [permission]. */
fun Collection<RbacRole>.hasPermission(permission: String): Boolean =
    any { permission in it.permissions }
