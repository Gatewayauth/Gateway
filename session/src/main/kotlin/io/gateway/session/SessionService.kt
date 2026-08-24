package io.gateway.session

import io.gateway.common.RandomTokens
import io.gateway.common.Sha256
import io.gateway.domain.model.Session
import io.gateway.domain.model.SessionId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.SessionRepository
import io.gateway.domain.time.Clock

/**
 * Issues and validates server-side sessions. The client only ever holds an opaque
 * random token; the database stores its SHA-256 hash, so a DB leak cannot be
 * replayed as a live session. All operations are scoped to a tenant.
 */
class SessionService(
    private val sessions: SessionRepository,
    private val clock: Clock,
    private val config: SessionConfig,
) {
    suspend fun create(
        tenantId: TenantId,
        userId: UserId,
        amr: Set<String>,
        ip: String?,
        userAgent: String?,
    ): IssuedSession {
        val rawToken = RandomTokens.urlSafe()
        val now = clock.now()
        val session = Session(
            id = SessionId.random(),
            userId = userId,
            tokenHash = Sha256.hashToBase64Url(rawToken),
            amr = amr,
            createdAt = now,
            lastSeenAt = now,
            expiresAt = now.plus(config.ttl),
            revokedAt = null,
            ip = ip,
            userAgent = userAgent,
        )
        return IssuedSession(sessions.insert(tenantId, session), rawToken)
    }

    /** Resolve an active session from the raw cookie token, sliding [Session.lastSeenAt]. */
    suspend fun resolve(tenantId: TenantId, rawToken: String): Session? {
        val hash = Sha256.hashToBase64Url(rawToken)
        val session = sessions.findByTokenHash(tenantId, hash) ?: return null
        val now = clock.now()
        if (!session.isActive(now)) return null
        // Throttle the write: only refresh lastSeenAt once it's older than the interval,
        // so routine authenticated requests don't each cost a DB update.
        if (now - session.lastSeenAt >= config.touchInterval) sessions.touch(tenantId, session.id, now)
        return session
    }

    suspend fun listActive(tenantId: TenantId, userId: UserId): List<Session> =
        sessions.listActiveForUser(tenantId, userId, clock.now())

    suspend fun findById(tenantId: TenantId, id: SessionId): Session? = sessions.findById(tenantId, id)

    suspend fun revoke(tenantId: TenantId, id: SessionId) = sessions.revoke(tenantId, id, clock.now())

    suspend fun revokeAll(tenantId: TenantId, userId: UserId) = sessions.revokeAllForUser(tenantId, userId, clock.now())
}
