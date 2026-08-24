package io.gateway.domain.repository

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId

/** Persistence port for recorded user→client scope consents. Tenant-scoped. */
interface ConsentRepository {
    /** True if the user has already consented to at least [scopes] for this client. */
    suspend fun hasConsent(tenantId: TenantId, userId: UserId, clientId: ClientId, scopes: Set<String>): Boolean

    /** Record consent, unioning with any previously granted scopes. */
    suspend fun grant(tenantId: TenantId, userId: UserId, clientId: ClientId, scopes: Set<String>)
}
