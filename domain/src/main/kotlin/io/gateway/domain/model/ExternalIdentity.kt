package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A link between a local [User] and an external provider account.
 * (provider, subject) is globally unique — it identifies the remote account.
 */
data class ExternalIdentity(
    val id: String,
    val userId: UserId,
    val provider: String,
    val subject: String,
    val email: String?,
    val createdAt: Instant,
)
