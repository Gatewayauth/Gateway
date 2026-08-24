package io.gateway.authexternal

import io.gateway.common.GatewayException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.http.ContentType
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Base OAuth2 authorization-code (+PKCE) connector. Subclasses implement only the
 * provider-specific [fetchProfile] mapping. The code exchange and authorize-URL
 * construction are shared.
 */
abstract class AbstractOAuth2Provider(
    final override val id: String,
    protected val settings: OAuth2ProviderSettings,
    protected val http: HttpClient,
) : IdentityProvider {

    override fun authorizeUrl(state: String, codeChallenge: String, redirectUri: String): String {
        val params = buildString {
            append("response_type=code")
            append("&client_id=").append(encode(settings.clientId))
            append("&redirect_uri=").append(encode(redirectUri))
            append("&scope=").append(encode(settings.scopes.joinToString(" ")))
            append("&state=").append(encode(state))
            append("&code_challenge=").append(encode(codeChallenge))
            append("&code_challenge_method=S256")
        }
        val separator = if (settings.authorizeUrl.contains('?')) '&' else '?'
        return "${settings.authorizeUrl}$separator$params"
    }

    override suspend fun exchange(code: String, codeVerifier: String, redirectUri: String): ExternalProfile {
        val tokenResponse: JsonObject = http.submitForm(
            url = settings.tokenUrl,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", settings.clientId)
                append("client_secret", settings.clientSecret)
                append("code_verifier", codeVerifier)
            },
        ) { accept(ContentType.Application.Json) }.body()

        val accessToken = tokenResponse["access_token"]?.jsonPrimitive?.content
            ?: throw GatewayException.Unauthenticated("$id did not return an access token.")
        return fetchProfile(accessToken)
    }

    /** Provider-specific: call the userinfo endpoint(s) and map to a normalized profile. */
    protected abstract suspend fun fetchProfile(accessToken: String): ExternalProfile

    protected fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
