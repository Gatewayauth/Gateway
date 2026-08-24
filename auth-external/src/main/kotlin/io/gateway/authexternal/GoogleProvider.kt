package io.gateway.authexternal

import io.gateway.common.GatewayException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlinx.serialization.json.JsonObject

/** Google OIDC connector. Userinfo returns standard OIDC claims. */
class GoogleProvider(settings: OAuth2ProviderSettings, http: HttpClient) :
    AbstractOAuth2Provider(ID, settings, http) {

    override suspend fun fetchProfile(accessToken: String): ExternalProfile {
        val info: JsonObject = http.get(settings.userInfoUrl) { bearerAuth(accessToken) }.body()
        val subject = info.str("sub")
            ?: throw GatewayException.Unauthenticated("Google userinfo missing sub.")
        return ExternalProfile(
            provider = ID,
            subject = subject,
            email = info.str("email"),
            emailVerified = info.bool("email_verified") ?: false,
            displayName = info.str("name"),
        )
    }

    companion object {
        const val ID = "google"
    }
}
