package io.gateway.app

import io.ktor.client.request.delete
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminApiTest {

    @Test
    fun listCreateDeleteClientsAndReadAudit() = testApplication {
        val ctx = gatewayTest()
        val http = createClient { followRedirects = false }
        val cookie = adminCookie(http, ctx)

        val clientId = http.post("/t/default/api/admin/clients") {
            sessionCookie(cookie)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"RP","redirect_uris":["https://rp.example/cb"],"public":true}""")
        }.bodyAsText().substringAfter("\"client_id\":\"").substringBefore("\"")

        val listed = http.get("/t/default/api/admin/clients") { sessionCookie(cookie) }
        assertEquals(HttpStatusCode.OK, listed.status)
        assertTrue(listed.bodyAsText().contains(clientId))

        val deleteUrl = "/t/default/api/admin/clients/$clientId"
        val deleted = http.delete(deleteUrl) { sessionCookie(cookie) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val afterDelete = http.get("/t/default/api/admin/clients") { sessionCookie(cookie) }
        assertFalse(afterDelete.bodyAsText().contains(clientId))

        val audit = http.get("/t/default/api/admin/audit") { sessionCookie(cookie) }
        assertEquals(HttpStatusCode.OK, audit.status)
        assertTrue(audit.bodyAsText().contains("CLIENT_CREATED"))
        assertTrue(audit.bodyAsText().contains("CLIENT_DELETED"))
    }

    @Test
    fun adminRequiresSession() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }
        assertEquals(HttpStatusCode.Unauthorized, http.get("/t/default/api/admin/clients").status)
    }

    @Test
    fun adminForbiddenForNonAdminSession() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }
        // A plain user (no admin role) may not reach the admin API.
        registerUser(http, "default", "plain@test.local")
        val cookie = login(http, "default", "plain@test.local")
        assertEquals(
            HttpStatusCode.Forbidden,
            http.get("/t/default/api/admin/clients") { sessionCookie(cookie) }.status,
        )
    }
}
