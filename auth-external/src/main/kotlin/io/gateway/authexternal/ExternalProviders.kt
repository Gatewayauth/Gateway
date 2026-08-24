package io.gateway.authexternal

import io.ktor.client.HttpClient

/**
 * Builds the [ProviderRegistry] from configured credentials, wiring each supported
 * provider with its well-known endpoints. A provider with a blank client id is
 * skipped (disabled).
 */
object ExternalProviders {

    fun build(credentials: Map<String, ExternalProviderCredentials>, http: HttpClient): ProviderRegistry {
        val providers = buildMap<String, IdentityProvider> {
            credentials[GoogleProvider.ID]?.enabled()?.let { put(GoogleProvider.ID, GoogleProvider(google(it), http)) }
            credentials[GitHubProvider.ID]?.enabled()?.let { put(GitHubProvider.ID, GitHubProvider(github(it), http)) }
            credentials[DiscordProvider.ID]?.enabled()
                ?.let { put(DiscordProvider.ID, DiscordProvider(discord(it), http)) }
        }
        return ProviderRegistry(providers)
    }

    private fun ExternalProviderCredentials.enabled(): ExternalProviderCredentials? =
        takeIf { it.clientId.isNotBlank() && it.clientSecret.isNotBlank() }

    private fun google(c: ExternalProviderCredentials) = OAuth2ProviderSettings(
        clientId = c.clientId,
        clientSecret = c.clientSecret,
        authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenUrl = "https://oauth2.googleapis.com/token",
        userInfoUrl = "https://openidconnect.googleapis.com/v1/userinfo",
        scopes = listOf("openid", "email", "profile"),
    )

    private fun github(c: ExternalProviderCredentials) = OAuth2ProviderSettings(
        clientId = c.clientId,
        clientSecret = c.clientSecret,
        authorizeUrl = "https://github.com/login/oauth/authorize",
        tokenUrl = "https://github.com/login/oauth/access_token",
        userInfoUrl = "https://api.github.com/user",
        scopes = listOf("read:user", "user:email"),
    )

    private fun discord(c: ExternalProviderCredentials) = OAuth2ProviderSettings(
        clientId = c.clientId,
        clientSecret = c.clientSecret,
        authorizeUrl = "https://discord.com/api/oauth2/authorize",
        tokenUrl = "https://discord.com/api/oauth2/token",
        userInfoUrl = "https://discord.com/api/users/@me",
        scopes = listOf("identify", "email"),
    )
}
