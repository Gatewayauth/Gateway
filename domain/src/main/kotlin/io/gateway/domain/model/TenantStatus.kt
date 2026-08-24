package io.gateway.domain.model

/** Lifecycle state of a tenant. A suspended tenant's endpoints stop resolving. */
enum class TenantStatus {
    ACTIVE,
    SUSPENDED,
}
