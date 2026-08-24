package io.gateway.oidc

/** Parsed `/oauth2/authorize` request parameters. */
data class AuthorizationRequest(
    val clientId: String,
    val redirectUri: String,
    val responseType: String,
    val scope: String,
    val state: String?,
    val nonce: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
)
