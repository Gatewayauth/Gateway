package io.gateway.oidc

/** Successful token-endpoint result, mapped by the app layer to the JSON response. */
data class TokenResult(
    val accessToken: String,
    val expiresInSeconds: Long,
    val scope: String,
    val idToken: String?,
    val refreshToken: String?,
    val tokenType: String = "Bearer",
)
