package io.gateway.app

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MfaFlowTest {

    private val codeGenerator = DefaultCodeGenerator()
    private val timeProvider = SystemTimeProvider()

    private fun currentCode(secret: String): String {
        val counter = Math.floorDiv(timeProvider.time, 30L)
        return codeGenerator.generate(secret, counter)
    }

    @Test
    fun enrollTotpThenMfaGatedLoginWithCodeAndRecovery() = testApplication {
        gatewayTest()
        val http = createClient { followRedirects = false }
        val email = "mfa-user@example.com"
        val password = "correcthorsebattery"

        register(http, email, password)
        val firstCookie = loginExpectingSession(http, email, password)

        // Enroll TOTP.
        val setup = http.post("/t/default/api/mfa/totp/setup") { header(HttpHeaders.Cookie, firstCookie) }
        assertEquals(HttpStatusCode.OK, setup.status)
        val secret = setup.bodyAsText().jsonValue("secret")

        val confirm = http.post("/t/default/api/mfa/totp/confirm") {
            header(HttpHeaders.Cookie, firstCookie)
            contentType(ContentType.Application.Json)
            setBody("""{"code":"${currentCode(secret)}"}""")
        }
        assertEquals(HttpStatusCode.OK, confirm.status)
        val recoveryCode = confirm.bodyAsText()
            .substringAfter("[").substringBefore("]")
            .split(",").first().trim().trim('"')
        assertTrue(recoveryCode.isNotBlank(), "must return recovery codes")

        // Now login is two-step: password yields a challenge, not a session.
        val pwLogin = login(http, email, password)
        assertEquals(HttpStatusCode.OK, pwLogin.status)
        assertTrue(pwLogin.headers[HttpHeaders.SetCookie] == null, "no session before second factor")
        assertTrue(pwLogin.bodyAsText().contains("mfaToken"))
        val mfaToken = pwLogin.bodyAsText().jsonValue("mfaToken")

        // Complete with a TOTP code -> full session.
        val mfaLogin = http.post("/t/default/api/auth/login/mfa") {
            contentType(ContentType.Application.Json)
            setBody("""{"mfaToken":"$mfaToken","code":"${currentCode(secret)}"}""")
        }
        assertEquals(HttpStatusCode.OK, mfaLogin.status)
        val sessionCookie = mfaLogin.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
        val me = http.get("/t/default/api/auth/me") { header(HttpHeaders.Cookie, sessionCookie) }
        assertEquals(HttpStatusCode.OK, me.status)

        // Recovery code works once...
        val recoveryToken = login(http, email, password).bodyAsText().jsonValue("mfaToken")
        val viaRecovery = http.post("/t/default/api/auth/login/mfa") {
            contentType(ContentType.Application.Json)
            setBody("""{"mfaToken":"$recoveryToken","code":"$recoveryCode"}""")
        }
        assertEquals(HttpStatusCode.OK, viaRecovery.status)

        // ...and cannot be reused.
        val reuseToken = login(http, email, password).bodyAsText().jsonValue("mfaToken")
        val reuse = http.post("/t/default/api/auth/login/mfa") {
            contentType(ContentType.Application.Json)
            setBody("""{"mfaToken":"$reuseToken","code":"$recoveryCode"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)
    }

    private suspend fun register(http: HttpClient, email: String, password: String) {
        http.post("/t/default/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
    }

    private suspend fun login(http: HttpClient, email: String, password: String): HttpResponse =
        http.post("/t/default/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }

    private suspend fun loginExpectingSession(http: HttpClient, email: String, password: String): String {
        val resp = login(http, email, password)
        return resp.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
    }

    private fun String.jsonValue(key: String): String = substringAfter("\"$key\":\"").substringBefore("\"")
}
