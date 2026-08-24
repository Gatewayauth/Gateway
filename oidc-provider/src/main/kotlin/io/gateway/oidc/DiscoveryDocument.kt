package io.gateway.oidc

/**
 * Builds the OIDC discovery metadata served at
 * `/.well-known/openid-configuration`. Returned as a plain map so the app layer
 * can serialize it with its own JSON stack without leaking a serialization dep here.
 */
object DiscoveryDocument {
    /** [issuer] is the tenant issuer (`{base}/t/{slug}`); all endpoints derive from it. */
    fun build(issuer: String): Map<String, Any> = mapOf(
        "issuer" to issuer,
        "authorization_endpoint" to "$issuer/oauth2/authorize",
        "token_endpoint" to "$issuer/oauth2/token",
        "userinfo_endpoint" to "$issuer/oauth2/userinfo",
        "jwks_uri" to "$issuer/.well-known/jwks.json",
        "response_types_supported" to listOf("code"),
        "grant_types_supported" to listOf("authorization_code", "refresh_token", "client_credentials"),
        "subject_types_supported" to listOf("public"),
        "id_token_signing_alg_values_supported" to listOf("RS256"),
        "token_endpoint_auth_methods_supported" to listOf("client_secret_basic", "client_secret_post", "none"),
        "code_challenge_methods_supported" to listOf("S256"),
        "scopes_supported" to listOf("openid", "profile", "email", "offline_access"),
        "claims_supported" to listOf("sub", "iss", "aud", "exp", "iat", "email", "email_verified", "name"),
    )
}
