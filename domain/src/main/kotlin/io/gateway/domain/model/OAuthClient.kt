package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A registered relying party (Grafana, YouTrack, ...). [public] clients (SPAs,
 * native apps) hold no secret and MUST use PKCE; confidential clients present a
 * secret whose hash is stored in [secretHash].
 */
data class OAuthClient(
    val id: ClientId,
    val name: String,
    val public: Boolean,
    val secretHash: String?,
    val redirectUris: Set<String>,
    val allowedScopes: Set<String>,
    val grantTypes: Set<GrantType>,
    val requirePkce: Boolean,
    val requireConsent: Boolean,
    val createdAt: Instant,
) {
    fun allowsRedirect(uri: String): Boolean = uri in redirectUris
}
