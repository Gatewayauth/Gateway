package io.gateway.domain.repository

import io.gateway.domain.model.AuthorizationGrant
import io.gateway.domain.model.TenantId
import kotlinx.datetime.Instant

/** Persistence port for single-use OIDC authorization codes. Tenant-scoped. */
interface AuthorizationCodeRepository {
    suspend fun insert(tenantId: TenantId, grant: AuthorizationGrant)

    /**
     * Atomically consume the code: returns the grant only if it exists, is not
     * expired, and was not already consumed — marking it consumed in the same
     * transaction. Returns null otherwise (including on reuse).
     */
    suspend fun consume(tenantId: TenantId, codeHash: String, now: Instant): AuthorizationGrant?
}
