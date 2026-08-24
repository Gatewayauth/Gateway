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
        gatewayTest()
        val http = createClient { followRedirects = false }
        val redirectUri = "https://rp.example/cb"

        http.post("/t/default/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"k@example.com","password":"correcthorsebattery"}""")
        }
        val cookie = http.post("/t/default/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"k@example.com","password":"correcthorsebattery"}""")
        }.headers[HttpHeaders.SetCookie]!!.substringBefore(';')

        val clientId = http.post("/t/default/api/admin/clients") {
            header("X-Admin-Token", "test-admin-token")
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

        val rotate = http.post("/t/default/api/admin/keys/rotate") { header("X-Admin-Token", "test-admin-token") }
        assertEquals(HttpStatusCode.OK, rotate.status)

        // Old (retired) key stays published so its tokens still verify.
        assertEquals(2, http.get("/t/default/.well-known/jwks.json").bodyAsText().countKeys())
        val userinfo = http.get("/t/default/oauth2/userinfo") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, userinfo.status)
    }
}
