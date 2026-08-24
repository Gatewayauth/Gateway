package io.gateway.authlocal

import io.gateway.domain.auth.PasswordHasher
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.notification.Mailer
import io.gateway.domain.repository.CredentialRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationServiceTest {

    private val users = mockk<UserRepository>(relaxed = true)
    private val credentials = mockk<CredentialRepository>(relaxed = true)
    private val hasher = mockk<PasswordHasher>(relaxed = true)
    private val mailer = mockk<Mailer>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val clock = Clock { now }
    private val service = RegistrationService(users, credentials, hasher, clock, mailer, "https://gw")

    private val password = "correcthorsebattery"

    private val tenant = io.gateway.domain.model.TenantId.DEFAULT

    @Test
    fun newEmailCreatesAccount() = runTest {
        coEvery { users.findByEmail(any(), "new@example.com") } returns null
        coEvery { users.insert(any(), any()) } answers { secondArg() }
        coEvery { hasher.hash(any()) } returns "argon2-hash"

        val outcome = service.register(tenant, "New@Example.com", "New User", password.toCharArray())

        assertTrue(outcome.created)
        assertEquals("new@example.com", outcome.user.email)
        coVerify(exactly = 1) { users.insert(any(), any()) }
        coVerify(exactly = 1) { credentials.upsertPasswordHash(any(), any(), "argon2-hash") }
        coVerify(exactly = 0) { mailer.send(any(), any(), any()) }
    }

    @Test
    fun existingEmailDoesNotLeakAndNotifies() = runTest {
        coEvery { users.findByEmail(any(), "taken@example.com") } returns User(
            id = UserId.random(),
            email = "taken@example.com",
            emailVerified = true,
            displayName = null,
            status = UserStatus.ACTIVE,
            mfaRequired = false,
            createdAt = now,
            updatedAt = now,
        )

        val outcome = service.register(tenant, "Taken@Example.com", null, password.toCharArray())

        assertFalse(outcome.created)
        assertEquals("taken@example.com", outcome.user.email)
        // No account or credential written; the existing address is notified instead.
        coVerify(exactly = 0) { users.insert(any(), any()) }
        coVerify(exactly = 0) { credentials.upsertPasswordHash(any(), any(), any()) }
        coVerify(exactly = 1) { mailer.send("taken@example.com", any(), any()) }
    }
}
