package io.gateway.domain.model

import kotlinx.datetime.Instant

/** A single-use, expiring account token (email verification / password reset). Stored hashed. */
data class AccountToken(
    val tokenHash: String,
    val userId: UserId,
    val purpose: AccountTokenPurpose,
    val expiresAt: Instant,
    val consumedAt: Instant?,
)
