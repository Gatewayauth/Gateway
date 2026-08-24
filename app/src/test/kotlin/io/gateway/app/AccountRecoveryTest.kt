package io.gateway.app

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

class AccountRecoveryTest {

    @Test
    fun forgotPasswordAlwaysSucceedsRegardlessOfAccount() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }
        val resp = http.post("/t/default/api/auth/password/forgot") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nobody@example.com"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun verifyWithBadTokenIsRejected() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }
        val resp = http.post("/t/default/api/auth/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"nonsense"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("validation_error"))
    }

    @Test
    fun resetWithBadTokenIsRejected() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }
        val resp = http.post("/t/default/api/auth/password/reset") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"nonsense","password":"correcthorsebattery"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }
}
