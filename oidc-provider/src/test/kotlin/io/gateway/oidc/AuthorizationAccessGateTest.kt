package io.gateway.oidc

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.GrantType
import io.gateway.domain.model.OAuthClient
import io.gateway.domain.model.RbacRole
import io.gateway.domain.model.RoleId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.AuthorizationCodeRepository
import io.gateway.domain.repository.ConsentRepository
import io.gateway.domain.repository.OAuthClientRepository
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.domain.time.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The per-client access gate: a client with [OAuthClient.requiredRoles] denies
 * users who hold none of them, at the authorize step, so they never reach the RP.
 */
class AuthorizationAccessGateTest {

    private val clients = mockk<OAuthClientRepository>()
    private val codes = mockk<AuthorizationCodeRepository>(relaxed = true)
    private val consents = mockk<ConsentRepository>(relaxed = true)
    private val roles = mockk<RbacRoleRepository>()
    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val clock = Clock { now }
    private val tenant = TenantId.DEFAULT
    private val userId = UserId.random()

    private val service =
        AuthorizationService(clients, codes, consents, roles, OidcConfig("https://gw"), clock)

    private fun client(requiredRoles: Set<String>) = OAuthClient(
        id = ClientId("grafana"),
        name = "Grafana",
        public = false,
        secretHash = "x",
        redirectUris = setOf("https://grafana/login/generic_oauth"),
        allowedScopes = setOf("openid", "profile", "email", "roles"),
        grantTypes = setOf(GrantType.AUTHORIZATION_CODE),
        requirePkce = false,
        requireConsent = false,
        requiredRoles = requiredRoles,
        createdAt = now,
    )

    private fun request() = AuthorizationRequest(
        clientId = "grafana",
        redirectUri = "https://grafana/login/generic_oauth",
        responseType = "code",
        scope = "openid profile email roles",
        state = "s",
        nonce = null,
        codeChallenge = null,
        codeChallengeMethod = null,
    )

    private fun role(slug: String) =
        RbacRole(RoleId.random(), tenant, slug, slug, null, emptySet(), now)

    @Test
    fun `denies user without any required role`() = runTest {
        coEvery { clients.findById(tenant, ClientId("grafana")) } returns
            client(setOf("grafana-admin", "grafana-viewer"))
        coEvery { roles.listForUser(tenant, userId) } returns emptyList()

        val e = assertFailsWith<OAuthException> { service.authorize(tenant, request(), userId) }
        assertEquals("access_denied", e.error)
        coVerify(exactly = 0) { codes.insert(any(), any()) }
    }

    @Test
    fun `allows user holding one of the required roles`() = runTest {
        coEvery { clients.findById(tenant, ClientId("grafana")) } returns
            client(setOf("grafana-admin", "grafana-viewer"))
        coEvery { roles.listForUser(tenant, userId) } returns listOf(role("grafana-viewer"))

        val location = service.authorize(tenant, request(), userId)
        assert(location.startsWith("https://grafana/login/generic_oauth?code="))
        coVerify(exactly = 1) { codes.insert(any(), any()) }
    }

    @Test
    fun `ungated client allows any authenticated user`() = runTest {
        coEvery { clients.findById(tenant, ClientId("grafana")) } returns client(emptySet())

        val location = service.authorize(tenant, request(), userId)
        assert(location.startsWith("https://grafana/login/generic_oauth?code="))
        // Roles are never consulted when the client declares none.
        coVerify(exactly = 0) { roles.listForUser(any(), any()) }
    }
}
