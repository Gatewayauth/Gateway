package io.gateway.domain.repository

import io.gateway.domain.model.Tenant
import io.gateway.domain.model.TenantId

/** Persistence port for tenants. */
interface TenantRepository {
    suspend fun findBySlug(slug: String): Tenant?

    suspend fun findById(id: TenantId): Tenant?

    suspend fun insert(tenant: Tenant): Tenant

    suspend fun list(): List<Tenant>
}
