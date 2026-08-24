package io.gateway.authlocal

import io.gateway.common.GatewayException
import io.gateway.domain.auth.PasswordHasher
import io.gateway.domain.model.AccountToken
import io.gateway.domain.model.AccountTokenPurpose
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.notification.Mailer
import io.gateway.domain.repository.AccountTokenRepository
import io.gateway.domain.repository.CredentialRepository
import io.gateway.domain.repository.SessionRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PasswordResetServiceTest {

    private val users = mockk<UserRepository>(relaxed = true)
    private val credentials = mockk<CredentialRepository>(relaxed = true)
    private val tokens = mockk<AccountTokenRepository>(relaxed = true)
    private val sessions = mockk<SessionRepository>(relaxed = true)
    private val hasher = mockk<PasswordHasher>(relaxed = true)
    private val mailer = mockk<Mailer>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val clock = Clock { now }
    private val service = PasswordResetService(
        users = users,
        credentials = credentials,
        tokens = tokens,
        sessions = sessions,
        hasher = hasher,
        mailer = mailer,
        clock = clock,
        linkBaseUrl = "https://gw",
    )

    private val userId = UserId.random()

    private fun account() = User(
        id = userId,
        email = "u@example.com",
        emailVerified = true,
        displayName = null,
        status = UserStatus.ACTIVE,
        mfaRequired = false,
        createdAt = now,
        updatedAt = now,
    )

    private val tenant = io.gateway.domain.model.TenantId.DEFAULT

    @Test
    fun requestForUnknownEmailIsSilent() = runTest {
        coEvery { users.findByEmail(any(), any()) } returns null
        service.request(tenant, "nobody@example.com")
        coVerify(exactly = 0) { tokens.insert(any(), any()) }
        coVerify(exactly = 0) { mailer.send(any(), any(), any()) }
    }

    @Test
    fun requestForKnownEmailIssuesTokenAndMails() = runTest {
        coEvery { users.findByEmail(any(), "u@example.com") } returns account()
        service.request(tenant, "u@example.com")
        coVerify(exactly = 1) { tokens.insert(any(), any()) }
        coVerify(exactly = 1) { mailer.send(any(), any(), any()) }
    }

    @Test
    fun resetReplacesPasswordAndRevokesSessions() = runTest {
        coEvery { tokens.consume(any(), any(), AccountTokenPurpose.PASSWORD_RESET, any()) } returns
            AccountToken(
                "h", userId, AccountTokenPurpose.PASSWORD_RESET,
                Instant.fromEpochMilliseconds(9_000_000), null,
            )
        coEvery { hasher.hash(any()) } returns "argon2-hash"

        service.reset(tenant, "raw", "correcthorsebattery".toCharArray())

        coVerify(exactly = 1) { credentials.upsertPasswordHash(any(), userId, "argon2-hash") }
        coVerify(exactly = 1) { sessions.revokeAllForUser(any(), userId, any()) }
    }

    @Test
    fun resetWithInvalidTokenFails() = runTest {
        coEvery { tokens.consume(any(), any(), any(), any()) } returns null
        assertFailsWith<GatewayException.Validation> {
            service.reset(tenant, "bad", "correcthorsebattery".toCharArray())
        }
    }

    @Test
    fun resetWithWeakPasswordFails() = runTest {
        coEvery { tokens.consume(any(), any(), AccountTokenPurpose.PASSWORD_RESET, any()) } returns
            AccountToken(
                "h", userId, AccountTokenPurpose.PASSWORD_RESET,
                Instant.fromEpochMilliseconds(9_000_000), null,
            )
        assertFailsWith<GatewayException.Validation> { service.reset(tenant, "raw", "short".toCharArray()) }
    }
}
