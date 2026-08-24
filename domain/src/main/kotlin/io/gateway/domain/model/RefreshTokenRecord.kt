package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A persisted refresh token (hash only). [familyId] groups a rotation chain so a
 * reused (already-rotated) token can trigger revocation of the whole family.
 */
data class RefreshTokenRecord(
    val tokenHash: String,
    val familyId: String,
    val clientId: ClientId,
    val userId: UserId,
    val scopes: Set<String>,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val rotatedAt: Instant?,
    val revokedAt: Instant?,
) {
    fun isUsable(now: Instant): Boolean =
        rotatedAt == null && revokedAt == null && now < expiresAt
}
