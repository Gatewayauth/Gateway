package io.gateway.authexternal

/** Endpoint + credential configuration for one external OAuth2/OIDC provider. */
data class OAuth2ProviderSettings(
    val clientId: String,
    val clientSecret: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val userInfoUrl: String,
    val scopes: List<String>,
)
