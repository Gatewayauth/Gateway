package io.gateway.authexternal

import io.gateway.common.GatewayException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlinx.serialization.json.JsonObject

/** Discord connector. `/users/@me` returns id, email, verified, username. */
class DiscordProvider(settings: OAuth2ProviderSettings, http: HttpClient) :
    AbstractOAuth2Provider(ID, settings, http) {

    override suspend fun fetchProfile(accessToken: String): ExternalProfile {
        val info: JsonObject = http.get(settings.userInfoUrl) { bearerAuth(accessToken) }.body()
        val subject = info.str("id")
            ?: throw GatewayException.Unauthenticated("Discord response missing id.")
        return ExternalProfile(
            provider = ID,
            subject = subject,
            email = info.str("email"),
            emailVerified = info.bool("verified") ?: false,
            displayName = info.str("global_name") ?: info.str("username"),
        )
    }

    companion object {
        const val ID = "discord"
    }
}
