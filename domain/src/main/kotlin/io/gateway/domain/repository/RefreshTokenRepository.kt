package io.gateway.domain.repository

import io.gateway.domain.model.RefreshTokenRecord
import io.gateway.domain.model.TenantId
import kotlinx.datetime.Instant

/** Persistence port for refresh tokens with rotation + reuse detection. Tenant-scoped. */
interface RefreshTokenRepository {
    suspend fun insert(tenantId: TenantId, token: RefreshTokenRecord)

    suspend fun findByHash(tenantId: TenantId, tokenHash: String): RefreshTokenRecord?

    /**
     * Atomically mark a not-yet-rotated token as rotated (consumed by a refresh).
     * Returns true if this call won the rotation, false if it was already rotated —
     * a concurrent replay the caller must treat as reuse.
     */
    suspend fun markRotated(tenantId: TenantId, tokenHash: String, now: Instant): Boolean

    /** Revoke every token in a family — used on detected reuse or explicit logout. */
    suspend fun revokeFamily(tenantId: TenantId, familyId: String, now: Instant)
}
