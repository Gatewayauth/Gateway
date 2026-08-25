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
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerTest {

    private fun ApplicationTestBuilder.configureGateway() {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        environment {
            config = MapApplicationConfig(
                "gateway.issuer" to "http://localhost:8080",
                "gateway.db.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "gateway.db.driver" to "org.h2.Driver",
                "gateway.db.user" to "sa",
                "gateway.db.password" to "",
                "gateway.db.maxPoolSize" to "4",
                "gateway.session.ttlHours" to "12",
                "gateway.session.cookieName" to "gw_session",
                "gateway.session.cookieSecure" to "false",
                "gateway.security.encKey" to "test-enc-key",
            )
        }
        application { module() }
    }

    @Test
    fun healthzReturnsOk() = testApplication {
        configureGateway()
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun discoveryDocumentIsServed() = testApplication {
        configureGateway()
        val response = client.get("/t/default/.well-known/openid-configuration")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"issuer\""), "discovery must contain issuer")
        assertTrue(body.contains("authorization_endpoint"), "discovery must advertise endpoints")
    }

    @Test
    fun jwksIsServed() = testApplication {
        configureGateway()
        val response = client.get("/t/default/.well-known/jwks.json")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"keys\""), "jwks must contain a keys array")
    }

    @Test
    fun registerThenLoginThenMe() = testApplication {
        configureGateway()
        val credentials = """{"email":"alice@example.com","password":"correcthorsebattery"}"""

        val register = client.post("/t/default/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"alice@example.com","password":"correcthorsebattery","displayName":"Alice"}""")
        }
        assertEquals(HttpStatusCode.Created, register.status)

        val login = client.post("/t/default/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val setCookie = login.headers[HttpHeaders.SetCookie]
        assertNotNull(setCookie, "login must set a session cookie")
        val cookiePair = setCookie.substringBefore(';')

        val me = client.get("/t/default/api/auth/me") {
            header(HttpHeaders.Cookie, cookiePair)
        }
        assertEquals(HttpStatusCode.OK, me.status)
        assertTrue(me.bodyAsText().contains("alice@example.com"))
    }

    @Test
    fun loginWithBadPasswordIsUnauthorized() = testApplication {
        configureGateway()
        client.post("/t/default/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"bob@example.com","password":"correcthorsebattery"}""")
        }
        val login = client.post("/t/default/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"bob@example.com","password":"wrongwrongwrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, login.status)
    }
}
