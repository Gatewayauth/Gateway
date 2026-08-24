package io.gateway.domain.repository

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import kotlinx.datetime.Instant

/** Persistence port for one-time MFA recovery codes (stored hashed). Tenant-scoped. */
interface RecoveryCodeRepository {
    /** Replace all of a user's recovery codes with the given hashes. */
    suspend fun replaceAll(tenantId: TenantId, userId: UserId, codeHashes: List<String>, now: Instant)

    /**
     * Atomically consume an unused recovery code matching [codeHash]. Returns true
     * if a code was consumed, false if none matched (wrong or already used).
     */
    suspend fun consume(tenantId: TenantId, userId: UserId, codeHash: String, now: Instant): Boolean

    suspend fun deleteAll(tenantId: TenantId, userId: UserId)
}
