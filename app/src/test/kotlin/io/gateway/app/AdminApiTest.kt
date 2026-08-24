package io.gateway.app

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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

    private val adminHeader = "X-Admin-Token" to "test-admin-token"

    @Test
    fun listCreateDeleteClientsAndReadAudit() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }

        val clientId = http.post("/t/default/api/admin/clients") {
            header(adminHeader.first, adminHeader.second)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"RP","redirect_uris":["https://rp.example/cb"],"public":true}""")
        }.bodyAsText().substringAfter("\"client_id\":\"").substringBefore("\"")

        val listed = http.get("/t/default/api/admin/clients") { header(adminHeader.first, adminHeader.second) }
        assertEquals(HttpStatusCode.OK, listed.status)
        assertTrue(listed.bodyAsText().contains(clientId))

        val deleteUrl = "/t/default/api/admin/clients/$clientId"
        val deleted = http.delete(deleteUrl) { header(adminHeader.first, adminHeader.second) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val afterDelete = http.get("/t/default/api/admin/clients") { header(adminHeader.first, adminHeader.second) }
        assertFalse(afterDelete.bodyAsText().contains(clientId))

        val audit = http.get("/t/default/api/admin/audit") { header(adminHeader.first, adminHeader.second) }
        assertEquals(HttpStatusCode.OK, audit.status)
        assertTrue(audit.bodyAsText().contains("CLIENT_CREATED"))
        assertTrue(audit.bodyAsText().contains("CLIENT_DELETED"))
    }

    @Test
    fun adminRequiresToken() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }
        assertEquals(HttpStatusCode.Unauthorized, http.get("/t/default/api/admin/clients").status)
    }
}
