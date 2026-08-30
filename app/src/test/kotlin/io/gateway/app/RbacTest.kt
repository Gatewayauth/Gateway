package io.gateway.app

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RbacTest {

    private val redirectUri = "https://rp.example/callback"

    @Test
    fun roleManagementGatedByOwnerAndAssignmentByAdmin() = testApplication {
        val ctx = gatewayTest()
        val http = createClient { followRedirects = false }
        val owner = adminCookie(http, ctx) // OWNER + super-admin

        // OWNER creates a role.
        val create = http.post("/t/default/api/admin/roles") {
            sessionCookie(owner)
            contentType(ContentType.Application.Json)
            setBody("""{"slug":"grafana-admin","name":"Grafana Admin","permissions":["dashboards:admin"]}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val roleId = create.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        // A plain (non-admin) user cannot even list.
        registerUser(http, "default", "plain@test.local")
        val plain = login(http, "default", "plain@test.local")
        assertEquals(
            HttpStatusCode.Forbidden,
            http.get("/t/default/api/admin/roles") { sessionCookie(plain) }.status,
        )

        // An ADMIN (not OWNER) can list + assign, but cannot create.
        registerUser(http, "default", "adm@test.local")
        ctx.promote("default", "adm@test.local", io.gateway.domain.model.Role.ADMIN, superAdmin = false)
        val adm = login(http, "default", "adm@test.local")
        assertEquals(HttpStatusCode.OK, http.get("/t/default/api/admin/roles") { sessionCookie(adm) }.status)
        val admCreate = http.post("/t/default/api/admin/roles") {
            sessionCookie(adm)
            contentType(ContentType.Application.Json)
            setBody("""{"slug":"nope","name":"Nope"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, admCreate.status)

        // ADMIN assigns the role to the plain user.
        val plainId = http.get("/t/default/api/auth/me") { sessionCookie(plain) }
            .bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")
        val assign = http.put("/t/default/api/admin/users/$plainId/roles") {
            sessionCookie(adm)
            contentType(ContentType.Application.Json)
            setBody("""{"roleIds":["$roleId"]}""")
        }
        assertEquals(HttpStatusCode.OK, assign.status)
        assertTrue(assign.bodyAsText().contains("grafana-admin"))

        // Read back.
        val readBack = http.get("/t/default/api/admin/users/$plainId/roles") { sessionCookie(adm) }
        assertTrue(readBack.bodyAsText().contains("grafana-admin"))
    }

    @Test
    fun rolesClaimEmittedOnlyWithRolesScope() = testApplication {
        val ctx = gatewayTest()
        val http = createClient { followRedirects = false }
        val owner = adminCookie(http, ctx)

        // Role + assign it to the owner (who will be the OIDC end user).
        val roleId = http.post("/t/default/api/admin/roles") {
            sessionCookie(owner)
            contentType(ContentType.Application.Json)
            setBody("""{"slug":"grafana-admin","name":"Grafana Admin"}""")
        }.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")
        val ownerId = http.get("/t/default/api/auth/me") { sessionCookie(owner) }
            .bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")
        http.put("/t/default/api/admin/users/$ownerId/roles") {
            sessionCookie(owner)
            contentType(ContentType.Application.Json)
            setBody("""{"roleIds":["$roleId"]}""")
        }

        // Client WITH the roles scope.
        val withRoles = createClientWithScopes(http, owner, """["openid","roles"]""")
        val idWith = authcodeIdToken(http, owner, withRoles, "openid%20roles")
        assertTrue(decodeJwtPayload(idWith).contains("grafana-admin"), "roles claim expected when roles scope granted")

        // Client WITHOUT the roles scope.
        val noRoles = createClientWithScopes(http, owner, """["openid"]""")
        val idNo = authcodeIdToken(http, owner, noRoles, "openid")
        assertFalse(decodeJwtPayload(idNo).contains("grafana-admin"), "no roles claim without roles scope")
    }

    @Suppress("MaxLineLength", "MaximumLineLength", "ArgumentListWrapping") // inline JSON test fixture
    private suspend fun createClientWithScopes(
        http: io.ktor.client.HttpClient,
        cookie: String,
        scopesJson: String,
    ): String = http.post("/t/default/api/admin/clients") {
        sessionCookie(cookie)
        contentType(ContentType.Application.Json)
        setBody("""{"name":"RP","redirect_uris":["$redirectUri"],"scopes":$scopesJson,"public":true,"require_consent":false}""")
    }.bodyAsText().substringAfter("\"client_id\":\"").substringBefore("\"")

    /** Runs the auth-code + PKCE flow and returns the id_token. */
    private suspend fun authcodeIdToken(
        http: io.ktor.client.HttpClient,
        cookie: String,
        clientId: String,
        scopeParam: String,
    ): String {
        val verifier = "verifier-abc-123-verifier-abc-123-verifier"
        val location = http.get(
            "/t/default/oauth2/authorize?response_type=code&client_id=$clientId" +
                "&redirect_uri=$redirectUri&scope=$scopeParam&state=s" +
                "&code_challenge=${pkceChallenge(verifier)}&code_challenge_method=S256",
        ) { header(HttpHeaders.Cookie, cookie) }.headers[HttpHeaders.Location]!!
        val code = location.substringAfter("code=").substringBefore("&")
        val body = http.submitForm(
            url = "/t/default/oauth2/token",
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("code_verifier", verifier)
            },
        ).bodyAsText()
        return body.substringAfter("\"id_token\":\"").substringBefore("\"")
    }

    private fun decodeJwtPayload(jwt: String): String =
        String(Base64.getUrlDecoder().decode(jwt.split(".")[1]))
}
