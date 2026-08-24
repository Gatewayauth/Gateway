package io.gateway.authlocal

import io.gateway.common.GatewayException
import io.gateway.domain.model.AccountToken
import io.gateway.domain.model.AccountTokenPurpose
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.notification.Mailer
import io.gateway.domain.repository.AccountTokenRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmailVerificationServiceTest {

    private val users = mockk<UserRepository>(relaxed = true)
    private val tokens = mockk<AccountTokenRepository>(relaxed = true)
    private val mailer = mockk<Mailer>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val clock = Clock { now }
    private val service = EmailVerificationService(users, tokens, mailer, clock, "https://gw")

    private fun user(status: UserStatus, verified: Boolean) = User(
        id = UserId.random(),
        email = "u@example.com",
        emailVerified = verified,
        displayName = null,
        status = status,
        mfaRequired = false,
        createdAt = now,
        updatedAt = now,
    )

    private val tenant = io.gateway.domain.model.TenantId.DEFAULT

    @Test
    fun verifyActivatesPendingUser() = runTest {
        val pending = user(UserStatus.PENDING_VERIFICATION, verified = false)
        coEvery { tokens.consume(any(), any(), AccountTokenPurpose.EMAIL_VERIFY, any()) } returns
            AccountToken(
                "h", pending.id, AccountTokenPurpose.EMAIL_VERIFY,
                Instant.fromEpochMilliseconds(9_000_000), null,
            )
        coEvery { users.findById(any(), pending.id) } returns pending
        coEvery { users.update(any(), any()) } answers { secondArg() }

        val result = service.verify(tenant, "raw")

        assertTrue(result.emailVerified)
        assertEquals(UserStatus.ACTIVE, result.status)
    }

    @Test
    fun verifyWithInvalidTokenFails() = runTest {
        coEvery { tokens.consume(any(), any(), any(), any()) } returns null
        assertFailsWith<GatewayException.Validation> { service.verify(tenant, "bad") }
    }

    @Test
    fun startVerificationSkipsAlreadyVerifiedUser() = runTest {
        service.startVerification(tenant, user(UserStatus.ACTIVE, verified = true))
        coVerify(exactly = 0) { tokens.insert(any(), any()) }
        coVerify(exactly = 0) { mailer.send(any(), any(), any()) }
    }

    @Test
    fun startVerificationSendsMail() = runTest {
        service.startVerification(tenant, user(UserStatus.PENDING_VERIFICATION, verified = false))
        coVerify(exactly = 1) { tokens.insert(any(), any()) }
        coVerify(exactly = 1) { mailer.send(any(), any(), any()) }
    }
}
