package io.gateway.app

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminUsersTest {

    private val email = "adminuser@example.com"

    @Test
    fun listUserAndDisableBlocksLogin() = testApplication {
        val ctx = gatewayTest()
        val http = createClient { followRedirects = false }
        val cookie = adminCookie(http, ctx)

        val userId = registerUser(http, "default", email)

        // Logs in fine before being disabled.
        assertEquals(HttpStatusCode.OK, login(http).status)

        val listed = http.get("/t/default/api/admin/users") { sessionCookie(cookie) }
        assertEquals(HttpStatusCode.OK, listed.status)
        assertTrue(listed.bodyAsText().contains(email))

        val get = http.get("/t/default/api/admin/users/$userId") { sessionCookie(cookie) }
        assertEquals(HttpStatusCode.OK, get.status)

        val disable = http.post("/t/default/api/admin/users/$userId/status") {
            sessionCookie(cookie)
            contentType(ContentType.Application.Json)
            setBody("""{"status":"DISABLED"}""")
        }
        assertEquals(HttpStatusCode.OK, disable.status)
        assertTrue(disable.bodyAsText().contains("DISABLED"))

        // Disabled account cannot authenticate.
        assertEquals(HttpStatusCode.Forbidden, login(http).status)
    }

    private suspend fun login(http: HttpClient) =
        http.post("/t/default/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$TEST_PASSWORD"}""")
        }
}
