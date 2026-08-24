package io.gateway.app

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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

class SessionManagementTest {

    private val email = "sess@example.com"
    private val password = "correcthorsebattery"

    private fun String.countIds(): Int = split("\"id\":\"").size - 1

    @Test
    fun listRevokeAndLogoutAllOwnSessions() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }

        http.post("/t/default/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        val cookieA = login(http)
        login(http) // a second active session

        val sessions = http.get("/t/default/api/auth/sessions") { header(HttpHeaders.Cookie, cookieA) }
        assertEquals(HttpStatusCode.OK, sessions.status)
        assertEquals(2, sessions.bodyAsText().countIds())

        // Delete the other (non-current) session, selected by its current flag rather
        // than list position (lastSeenAt isn't bumped on every request).
        val otherId = sessions.bodyAsText().split("},{")
            .first { it.contains("\"current\":false") }
            .substringAfter("\"id\":\"").substringBefore("\"")
        val deleted = http.delete("/t/default/api/auth/sessions/$otherId") { header(HttpHeaders.Cookie, cookieA) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        val remaining = http.get("/t/default/api/auth/sessions") { header(HttpHeaders.Cookie, cookieA) }
        assertEquals(1, remaining.bodyAsText().countIds())

        assertEquals(
            HttpStatusCode.OK,
            http.post("/t/default/api/auth/logout-all") { header(HttpHeaders.Cookie, cookieA) }.status,
        )
        // Cookie A's session is now revoked.
        assertEquals(
            HttpStatusCode.Unauthorized,
            http.get("/t/default/api/auth/me") { header(HttpHeaders.Cookie, cookieA) }.status,
        )
    }

    private suspend fun login(http: HttpClient): String =
        http.post("/t/default/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
}
