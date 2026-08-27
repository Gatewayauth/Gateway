package io.gateway.oidc

import io.gateway.common.Sha256
import io.gateway.domain.model.ClientId
import io.gateway.domain.model.OAuthClient
import io.gateway.domain.model.RefreshTokenRecord
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.repository.AuthorizationCodeRepository
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.domain.repository.RefreshTokenRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RefreshTokenReuseTest {

    private val codes = mockk<AuthorizationCodeRepository>(relaxed = true)
    private val refreshTokens = mockk<RefreshTokenRepository>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private val roles = mockk<RbacRoleRepository>(relaxed = true)
    private val jwtIssuer = mockk<JwtIssuer>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val clock = Clock { now }
    private val tenant = TenantId.DEFAULT
    private val service = TokenService(codes, refreshTokens, users, roles, jwtIssuer, OidcConfig("https://gw"), clock)

    private val client = OAuthClient(
        id = ClientId("client-1"),
        name = "App",
        public = false,
        secretHash = "x",
        redirectUris = setOf("https://app/cb"),
        allowedScopes = setOf("openid", "offline_access"),
        grantTypes = emptySet(),
        requirePkce = false,
        requireConsent = false,
        createdAt = now,
    )

    private fun record() = RefreshTokenRecord(
        tokenHash = Sha256.hashToBase64Url("raw-refresh"),
        familyId = "fam-1",
        clientId = client.id,
        userId = UserId.random(),
        scopes = setOf("openid", "offline_access"),
        issuedAt = now,
        expiresAt = Instant.fromEpochMilliseconds(9_999_999_999),
        rotatedAt = null,
        revokedAt = null,
    )

    private fun user(id: UserId) = User(
        id = id,
        email = "u@example.com",
        emailVerified = true,
        displayName = null,
        status = UserStatus.ACTIVE,
        mfaRequired = false,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun happyPathRotatesAndIssues() = runTest {
        val rec = record()
        coEvery { refreshTokens.findByHash(any(), any()) } returns rec
        coEvery { refreshTokens.markRotated(any(), any(), any()) } returns true
        coEvery { users.findById(any(), rec.userId) } returns user(rec.userId)

        val result = service.refreshTokenGrant(tenant, "https://gw", client, "raw-refresh")

        assertNotNull(result.refreshToken)
        coVerify(exactly = 1) { refreshTokens.insert(any(), any()) }
        coVerify(exactly = 0) { refreshTokens.revokeFamily(any(), any(), any()) }
    }

    @Test
    fun alreadyRotatedTokenIsReuseAndRevokesFamily() = runTest {
        val rec = record().copy(rotatedAt = now)
        coEvery { refreshTokens.findByHash(any(), any()) } returns rec

        assertFailsWith<OAuthException> { service.refreshTokenGrant(tenant, "https://gw", client, "raw-refresh") }
        coVerify(exactly = 1) { refreshTokens.revokeFamily(any(), "fam-1", any()) }
        coVerify(exactly = 0) { refreshTokens.insert(any(), any()) }
    }

    @Test
    fun losingTheRotationRaceIsTreatedAsReuse() = runTest {
        val rec = record()
        coEvery { refreshTokens.findByHash(any(), any()) } returns rec
        // A concurrent refresh already claimed the rotation: our conditional update loses.
        coEvery { refreshTokens.markRotated(any(), any(), any()) } returns false

        assertFailsWith<OAuthException> { service.refreshTokenGrant(tenant, "https://gw", client, "raw-refresh") }
        coVerify(exactly = 1) { refreshTokens.revokeFamily(any(), "fam-1", any()) }
        coVerify(exactly = 0) { refreshTokens.insert(any(), any()) }
    }
}
