package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * Server-side authenticated browser session. The opaque cookie value the client
 * holds hashes to [tokenHash]; the raw value is never stored. [amr] records how
 * the user authenticated (password, otp, external) for step-up decisions.
 */
data class Session(
    val id: SessionId,
    val userId: UserId,
    val tokenHash: String,
    val amr: Set<String>,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val ip: String?,
    val userAgent: String?,
) {
    fun isActive(now: Instant): Boolean = revokedAt == null && now < expiresAt
}
