package io.gateway.app

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OidcFlowTest {

    private val redirectUri = "https://rp.example/callback"

    @Test
    fun authorizationCodePkceFlowWithRefreshAndReuseDetection() = testApplication {
        val ctx = gatewayTest()
        val noRedirect = createClient { followRedirects = false }

        // 1. A user account + session. This user also owns the tenant, so their
        // session authorizes the admin client registration below.
        registerUser(noRedirect, "default", "rp-user@example.com")
        ctx.promote("default", "rp-user@example.com", io.gateway.domain.model.Role.OWNER, superAdmin = true)
        val cookie = login(noRedirect, "default", "rp-user@example.com")

        // 2. Register a public relying party.
        val createClientResp = noRedirect.post("/t/default/api/admin/clients") {
            sessionCookie(cookie)
            contentType(ContentType.Application.Json)
            setBody(
                """{"name":"RP","redirect_uris":["$redirectUri"],""" +
                    """"scopes":["openid","profile","email","offline_access"],""" +
                    """"public":true,"require_consent":false}""",
            )
        }
        assertEquals(HttpStatusCode.Created, createClientResp.status)
        val clientId = createClientResp.bodyAsText().substringAfter("\"client_id\":\"").substringBefore("\"")

        // 3. Authorize with PKCE -> 302 redirect carrying the code.
        val verifier = "verifier-abc-123-verifier-abc-123-verifier"
        val challenge = pkceChallenge(verifier)
        val authorize = noRedirect.get(
            "/t/default/oauth2/authorize?response_type=code&client_id=$clientId" +
                "&redirect_uri=$redirectUri&scope=openid%20profile%20email%20offline_access" +
                "&state=xyz&code_challenge=$challenge&code_challenge_method=S256",
        ) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.Found, authorize.status)
        val location = authorize.headers[HttpHeaders.Location]!!
        assertTrue(location.startsWith(redirectUri), "must redirect to the registered URI")
        assertTrue(location.contains("state=xyz"))
        val code = location.substringAfter("code=").substringBefore("&")

        // 4. Exchange the code for tokens.
        val tokenResp = noRedirect.submitForm(
            url = "/t/default/oauth2/token",
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("code_verifier", verifier)
            },
        )
        assertEquals(HttpStatusCode.OK, tokenResp.status)
        val tokenBody = tokenResp.bodyAsText()
        val accessToken = tokenBody.jsonString("access_token")
        assertTrue(tokenBody.contains("id_token"), "openid scope must yield an id_token")
        val refreshToken = tokenBody.jsonString("refresh_token")

        // 5. Userinfo with the access token.
        val userinfo = noRedirect.get("/t/default/oauth2/userinfo") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, userinfo.status)
        assertTrue(userinfo.bodyAsText().contains("rp-user@example.com"))

        // 6. Refresh -> new tokens.
        val refreshed = refresh(noRedirect, clientId, refreshToken)
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val newRefresh = refreshed.bodyAsText().jsonString("refresh_token")
        assertNotNull(newRefresh)

        // 7. Reusing the now-rotated refresh token is rejected.
        val reuse = refresh(noRedirect, clientId, refreshToken)
        assertEquals(HttpStatusCode.BadRequest, reuse.status)
        assertTrue(reuse.bodyAsText().contains("invalid_grant"))
    }

    @Test
    fun authorizeWithoutSessionReturnsLoginRequired() = testApplication {
        gatewayTest()
        val client = createClient { followRedirects = false }
        val resp = client.get("/t/default/oauth2/authorize?response_type=code&client_id=x&redirect_uri=y&scope=openid")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertTrue(resp.bodyAsText().contains("login_required"))
    }

    private suspend fun refresh(
        client: io.ktor.client.HttpClient,
        clientId: String,
        refreshToken: String,
    ): HttpResponse = client.submitForm(
        url = "/t/default/oauth2/token",
        formParameters = parameters {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", clientId)
        },
    )

    private fun String.jsonString(key: String): String = substringAfter("\"$key\":\"").substringBefore("\"")
}
