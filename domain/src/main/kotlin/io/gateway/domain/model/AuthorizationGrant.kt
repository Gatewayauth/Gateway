package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A persisted OIDC authorization code (single-use). Only the SHA-256 [codeHash]
 * of the code is stored. Binds the code to the client, user, redirect URI, and
 * PKCE challenge so the token exchange can verify all of them.
 */
data class AuthorizationGrant(
    val codeHash: String,
    val clientId: ClientId,
    val userId: UserId,
    val redirectUri: String,
    val scopes: Set<String>,
    val nonce: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
    val authTime: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant?,
)
