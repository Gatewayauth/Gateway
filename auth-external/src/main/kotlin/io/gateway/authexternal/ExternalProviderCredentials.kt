package io.gateway.authexternal

/** Per-provider client credentials from configuration. */
data class ExternalProviderCredentials(
    val clientId: String,
    val clientSecret: String,
)
