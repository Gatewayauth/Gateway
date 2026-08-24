package io.gateway.app

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsentFlowTest {

    private val redirectUri = "https://rp.example/cb"

    @Test
    fun authorizeRequiresConsentUntilGranted() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }

        http.post("/t/default/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"c@example.com","password":"correcthorsebattery"}""")
        }
        val cookie = http.post("/t/default/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"c@example.com","password":"correcthorsebattery"}""")
        }.headers[HttpHeaders.SetCookie]!!.substringBefore(';')

        val clientId = http.post("/t/default/api/admin/clients") {
            header("X-Admin-Token", "test-admin-token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"name":"RP","redirect_uris":["$redirectUri"],""" +
                    """"scopes":["openid","profile"],"public":true,"require_consent":true}""",
            )
        }.bodyAsText().substringAfter("\"client_id\":\"").substringBefore("\"")

        val authorizeUrl = "/t/default/oauth2/authorize" +
            "?response_type=code&client_id=$clientId&redirect_uri=$redirectUri" +
            "&scope=openid%20profile&state=s&code_challenge=${pkceChallenge("verifier-abc-123-verifier-abc")}" +
            "&code_challenge_method=S256"

        // First attempt: consent required, no redirect.
        val first = http.get(authorizeUrl) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(first.bodyAsText().contains("consent_required"))
        assertTrue(first.bodyAsText().contains("client_name"))

        // Grant consent.
        val granted = http.post("/t/default/oauth2/consent") {
            header(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.Json)
            setBody("""{"client_id":"$clientId","scopes":["openid","profile"]}""")
        }
        assertEquals(HttpStatusCode.OK, granted.status)

        // Second attempt: now issues a code.
        val second = http.get(authorizeUrl) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.Found, second.status)
        assertTrue(second.headers[HttpHeaders.Location]!!.contains("code="))
    }
}
