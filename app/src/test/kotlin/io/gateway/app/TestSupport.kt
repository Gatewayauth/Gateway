@file:Suppress("MatchingDeclarationName") // support file bundles TestContext with shared test helpers

package io.gateway.app

import io.gateway.domain.model.Role
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.datetime.Clock
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Handle to the running test app's repositories, for seeding privileged users. */
class TestContext {
    lateinit var users: UserRepository
    lateinit var tenants: TenantRepository

    /** Promotes an already-registered user to the given role / super-admin flag. */
    suspend fun promote(tenantSlug: String, email: String, role: Role, superAdmin: Boolean) {
        val tid = tenants.findBySlug(tenantSlug)!!.id
        val user = users.findByEmail(tid, email)!!
        users.update(tid, user.copy(role = role, superAdmin = superAdmin, updatedAt = Clock.System.now()))
    }
}

const val TEST_PASSWORD = "correcthorsebattery"

/** Configures a test Gateway instance with an isolated in-memory H2 database. */
fun ApplicationTestBuilder.gatewayTest(): TestContext {
    val ctx = TestContext()
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
    application {
        module()
        val users by inject<UserRepository>()
        val tenants by inject<TenantRepository>()
        ctx.users = users
        ctx.tenants = tenants
    }
    return ctx
}

/**
 * Registers a user, promotes them (default OWNER + super-admin in the default
 * tenant), logs in, and returns the `gw_session=...` cookie for admin requests.
 */
suspend fun ApplicationTestBuilder.adminCookie(
    http: HttpClient,
    ctx: TestContext,
    tenant: String = "default",
    email: String = "owner@test.local",
    role: Role = Role.OWNER,
    superAdmin: Boolean = tenant == "default",
): String {
    registerUser(http, tenant, email)
    ctx.promote(tenant, email, role, superAdmin)
    return login(http, tenant, email)
}

/** Registers a plain user; returns the new user id. */
suspend fun registerUser(http: HttpClient, tenant: String, email: String, password: String = TEST_PASSWORD): String =
    http.post("/t/$tenant/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","password":"$password"}""")
    }.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

/** Logs in and returns the `gw_session=...` cookie value. */
suspend fun login(http: HttpClient, tenant: String, email: String, password: String = TEST_PASSWORD): String =
    http.post("/t/$tenant/api/auth/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","password":"$password"}""")
    }.headers[HttpHeaders.SetCookie]!!.substringBefore(';')

/** Attaches a session cookie to a request. */
fun HttpRequestBuilder.sessionCookie(cookie: String) {
    header(HttpHeaders.Cookie, cookie)
}

/** Computes the S256 PKCE challenge for a verifier. */
fun pkceChallenge(verifier: String): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
