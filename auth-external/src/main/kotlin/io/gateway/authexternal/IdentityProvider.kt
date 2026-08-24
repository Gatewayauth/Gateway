package io.gateway.authexternal

/**
 * SPI for an external identity provider (Google, GitHub, Discord, ...).
 * Adding a provider means implementing this interface and registering it — the
 * OIDC/login core never changes. Each provider maps its OAuth2 flow to a
 * normalized [ExternalProfile].
 */
interface IdentityProvider {
    /** Stable provider key, e.g. "google", persisted in `external_identities.provider`. */
    val id: String

    /** Build the provider authorize URL for the given state + PKCE challenge. */
    fun authorizeUrl(state: String, codeChallenge: String, redirectUri: String): String

    /** Exchange the returned code for the user's normalized profile. */
    suspend fun exchange(code: String, codeVerifier: String, redirectUri: String): ExternalProfile
}
