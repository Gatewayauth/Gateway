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
import io.gateway.domain.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TenantIsolationTest {

    @Test
    fun tenantsAreIsolated() = testApplication {
        val ctx = gatewayTest()
        val http = createClient { followRedirects = false }

        // Super-admin (default tenant) provisions a second tenant.
        val superCookie = adminCookie(http, ctx)
        val created = http.post("/api/provisioning/tenants") {
            sessionCookie(superCookie)
            contentType(ContentType.Application.Json)
            setBody("""{"slug":"acme","name":"Acme"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)

        registerUser(http, "default", "alice@example.com")
        registerUser(http, "acme", "bob@example.com")

        // Each tenant's admin view sees only its own users.
        val defaultUsers = adminUsers(http, "default", superCookie)
        assertTrue(defaultUsers.contains("alice@example.com"))
        assertFalse(defaultUsers.contains("bob@example.com"))

        val acmeCookie = adminCookie(http, ctx, tenant = "acme", email = "acmeadmin@test.local", role = Role.OWNER)
        val acmeUsers = adminUsers(http, "acme", acmeCookie)
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

    private suspend fun adminUsers(http: HttpClient, tenant: String, cookie: String): String =
        http.get("/t/$tenant/api/admin/users") { sessionCookie(cookie) }.bodyAsText()
}
