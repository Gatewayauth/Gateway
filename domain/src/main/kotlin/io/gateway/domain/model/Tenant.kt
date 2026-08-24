package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * An isolated tenant. [slug] is the URL segment (`/t/{slug}/…`) and is unique;
 * [id] is the stable key stored on every tenant-scoped row.
 */
data class Tenant(
    val id: TenantId,
    val slug: String,
    val name: String,
    val status: TenantStatus,
    val createdAt: Instant,
) {
    val isActive: Boolean get() = status == TenantStatus.ACTIVE
}
