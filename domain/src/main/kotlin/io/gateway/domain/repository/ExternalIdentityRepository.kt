package io.gateway.domain.repository

import io.gateway.domain.model.ExternalIdentity
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId

/** Persistence port for external-provider identity links. Tenant-scoped. */
interface ExternalIdentityRepository {
    suspend fun findByProviderSubject(tenantId: TenantId, provider: String, subject: String): ExternalIdentity?

    suspend fun listForUser(tenantId: TenantId, userId: UserId): List<ExternalIdentity>

    suspend fun insert(tenantId: TenantId, identity: ExternalIdentity): ExternalIdentity
}
