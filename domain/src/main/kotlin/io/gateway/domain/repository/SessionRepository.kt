package io.gateway.domain.repository

import io.gateway.domain.model.Session
import io.gateway.domain.model.SessionId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId

/** Persistence port for server-side sessions. All operations are scoped to a tenant. */
interface SessionRepository {
    suspend fun findByTokenHash(tenantId: TenantId, tokenHash: String): Session?

    suspend fun findById(tenantId: TenantId, id: SessionId): Session?

    /** Active (non-revoked, unexpired) sessions for a user, newest first. */
    suspend fun listActiveForUser(tenantId: TenantId, userId: UserId, now: kotlinx.datetime.Instant): List<Session>

    suspend fun insert(tenantId: TenantId, session: Session): Session

    suspend fun touch(tenantId: TenantId, id: SessionId, lastSeenAt: kotlinx.datetime.Instant)

    suspend fun revoke(tenantId: TenantId, id: SessionId, revokedAt: kotlinx.datetime.Instant)

    suspend fun revokeAllForUser(tenantId: TenantId, userId: UserId, revokedAt: kotlinx.datetime.Instant)
}
