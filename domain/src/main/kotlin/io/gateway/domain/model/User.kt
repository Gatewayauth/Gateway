package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A Gateway account. Email is the canonical identity for local login and for
 * linking external identities. [emailVerified] gates sensitive linking flows to
 * prevent account takeover via unverified addresses.
 */
data class User(
    val id: UserId,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String?,
    val status: UserStatus,
    val mfaRequired: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
