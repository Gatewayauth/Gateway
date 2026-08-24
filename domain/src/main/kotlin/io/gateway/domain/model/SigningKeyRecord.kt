package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A persisted JWT signing key. [publicJwk] is the public JWK JSON (published in
 * JWKS); [privateKeyEnc] is the full key pair JSON encrypted at rest. [active] marks
 * the current signing key; retired keys stay published (until [expiresAt]) so
 * tokens they signed still verify.
 */
data class SigningKeyRecord(
    val kid: String,
    val algorithm: String,
    val publicJwk: String,
    val privateKeyEnc: String,
    val active: Boolean,
    val createdAt: Instant,
    val expiresAt: Instant?,
) {
    fun isPublishable(now: Instant): Boolean = active || expiresAt == null || now < expiresAt
}
