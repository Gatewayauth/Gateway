package io.gateway.oidc

/** Verified access-token claims needed for authorization decisions. */
data class AccessTokenClaims(
    val subject: String,
    val scopes: Set<String>,
)
