package io.gateway.app

import io.ktor.client.HttpClient
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TenantIsolationTest {

    private val admin = "X-Admin-Token" to "test-admin-token"
    private val password = "correcthorsebattery"

    @Test
    fun tenantsAreIsolated() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }

        // Provision a second tenant alongside the seeded "default".
        val created = http.post("/api/provisioning/tenants") {
            header(admin.first, admin.second)
            contentType(ContentType.Application.Json)
            setBody("""{"slug":"acme","name":"Acme"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)

        register(http, "default", "alice@example.com")
        register(http, "acme", "bob@example.com")

        // Each tenant's admin view sees only its own users.
        val defaultUsers = adminUsers(http, "default")
        assertTrue(defaultUsers.contains("alice@example.com"))
        assertFalse(defaultUsers.contains("bob@example.com"))

        val acmeUsers = adminUsers(http, "acme")
        assertTrue(acmeUsers.contains("bob@example.com"))
        assertFalse(acmeUsers.contains("alice@example.com"))

        // A session issued for one tenant is not valid on another.
        val cookie = login(http, "default", "alice@example.com")
        assertEquals(
            HttpStatusCode.OK,
            http.get("/t/default/api/auth/me") { header(HttpHeaders.Cookie, cookie) }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            http.get("/t/acme/api/auth/me") { header(HttpHeaders.Cookie, cookie) }.status,
        )
    }

    private suspend fun register(http: HttpClient, tenant: String, email: String) {
        val res = http.post("/t/$tenant/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.Created, res.status)
    }

    private suspend fun adminUsers(http: HttpClient, tenant: String): String =
        http.get("/t/$tenant/api/admin/users") { header(admin.first, admin.second) }.bodyAsText()

    private suspend fun login(http: HttpClient, tenant: String, email: String): String =
        http.post("/t/$tenant/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
}
