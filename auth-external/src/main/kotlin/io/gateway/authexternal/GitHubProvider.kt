package io.gateway.authexternal

import io.gateway.common.GatewayException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * GitHub connector. The user's primary verified email comes from the separate
 * `/user/emails` endpoint (the profile email may be private/unverified).
 */
class GitHubProvider(settings: OAuth2ProviderSettings, http: HttpClient) :
    AbstractOAuth2Provider(ID, settings, http) {

    override suspend fun fetchProfile(accessToken: String): ExternalProfile {
        val user: JsonObject = http.get(settings.userInfoUrl) { githubHeaders(accessToken) }.body()
        val subject = user.str("id")
            ?: throw GatewayException.Unauthenticated("GitHub response missing id.")

        val emails: JsonArray = http.get("${settings.userInfoUrl}/emails") { githubHeaders(accessToken) }.body()
        val primary = emails.map { it.jsonObject }
            .firstOrNull { it.bool("primary") == true && it.bool("verified") == true }

        return ExternalProfile(
            provider = ID,
            subject = subject,
            email = primary?.str("email") ?: user.str("email"),
            emailVerified = primary != null,
            displayName = user.str("name") ?: user.str("login"),
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.githubHeaders(accessToken: String) {
        bearerAuth(accessToken)
        header(HttpHeaders.UserAgent, "Gateway")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    companion object {
        const val ID = "github"
    }
}
