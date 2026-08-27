package io.gateway.domain.repository

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.OAuthClient
import io.gateway.domain.model.TenantId

/** Persistence port for registered OAuth2 relying parties. Tenant-scoped. */
interface OAuthClientRepository {
    suspend fun findById(tenantId: TenantId, id: ClientId): OAuthClient?

    suspend fun list(tenantId: TenantId): List<OAuthClient>

    suspend fun insert(tenantId: TenantId, client: OAuthClient): OAuthClient

    /** Updates a mutable client's fields in place. `secretHash`/`createdAt` are left untouched. */
    suspend fun update(tenantId: TenantId, client: OAuthClient): OAuthClient

    suspend fun delete(tenantId: TenantId, id: ClientId)
}
