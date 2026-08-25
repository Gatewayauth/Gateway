package io.gateway.app

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyRotationTest {

    private fun String.countKeys(): Int = split("\"kid\"").size - 1

    @Test
    fun rotationPublishesBothKeysAndKeepsOldTokensValid() = testApplication {
        val ctx = gatewayTest()
        val http = createClient { followRedirects = false }
        val redirectUri = "https://rp.example/cb"

        // The end user is also the tenant owner, so their session drives both the
        // OIDC flow and the admin calls.
        registerUser(http, "default", "k@example.com")
        ctx.promote("default", "k@example.com", io.gateway.domain.model.Role.OWNER, superAdmin = true)
        val cookie = login(http, "default", "k@example.com")

        val clientId = http.post("/t/default/api/admin/clients") {
            sessionCookie(cookie)
            contentType(ContentType.Application.Json)
            setBody(
                """{"name":"RP","redirect_uris":["$redirectUri"],"scopes":["openid"],""" +
                    """"public":true,"require_consent":false}""",
            )
        }.bodyAsText().substringAfter("\"client_id\":\"").substringBefore("\"")

        val verifier = "verifier-abc-123-verifier-abc-123-verifier"
        val location = http.get(
            "/t/default/oauth2/authorize?response_type=code&client_id=$clientId&redirect_uri=$redirectUri" +
                "&scope=openid&state=s&code_challenge=${pkceChallenge(verifier)}&code_challenge_method=S256",
        ) { header(HttpHeaders.Cookie, cookie) }.headers[HttpHeaders.Location]!!
        val code = location.substringAfter("code=").substringBefore("&")

        val accessToken = http.submitForm(
            url = "/t/default/oauth2/token",
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("code_verifier", verifier)
            },
        ).bodyAsText().substringAfter("\"access_token\":\"").substringBefore("\"")

        assertEquals(1, http.get("/t/default/.well-known/jwks.json").bodyAsText().countKeys())

        val rotate = http.post("/t/default/api/admin/keys/rotate") { sessionCookie(cookie) }
        assertEquals(HttpStatusCode.OK, rotate.status)

        // Old (retired) key stays published so its tokens still verify.
        assertEquals(2, http.get("/t/default/.well-known/jwks.json").bodyAsText().countKeys())
        val userinfo = http.get("/t/default/oauth2/userinfo") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, userinfo.status)
    }
}
