package io.gateway.app.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** OAuth2/OIDC endpoint payloads (snake_case per the specs). Nested to keep one type per file. */
object OAuthDtos {

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String,
        @SerialName("expires_in") val expiresIn: Long,
        val scope: String,
        @SerialName("id_token") val idToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    @Serializable
    data class ErrorResponse(
        val error: String,
        @SerialName("error_description") val errorDescription: String,
    )

    /** Returned by /authorize when the user must approve scopes for a client. */
    @Serializable
    data class ConsentRequired(
        @SerialName("consent_required") val consentRequired: Boolean = true,
        @SerialName("client_id") val clientId: String,
        @SerialName("client_name") val clientName: String,
        val scopes: List<String>,
    )

    @Serializable
    data class ConsentRequest(
        @SerialName("client_id") val clientId: String,
        val scopes: Set<String>,
    )

    // Claims beyond `sub` are populated only when the access token carries the
    // matching scope; `explicitNulls = false` drops the unset ones from the JSON.
    @Serializable
    data class UserInfo(
        val sub: String,
        val email: String? = null,
        @SerialName("email_verified") val emailVerified: Boolean? = null,
        val name: String? = null,
    )
}
