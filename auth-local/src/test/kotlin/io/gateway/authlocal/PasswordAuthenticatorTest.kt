package io.gateway.authlocal

import io.gateway.common.GatewayException
import io.gateway.domain.auth.PasswordHasher
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.repository.CredentialRepository
import io.gateway.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PasswordAuthenticatorTest {

    private val users = mockk<UserRepository>()
    private val credentials = mockk<CredentialRepository>(relaxed = true)
    private val hasher = mockk<PasswordHasher>()

    // hash() is called once at construction to build the dummy hash.
    private val dummy = "DUMMY-HASH"

    private fun authenticator(): PasswordAuthenticator {
        every { hasher.hash(any()) } returns dummy
        return PasswordAuthenticator(users, credentials, hasher)
    }

    private val tenant = io.gateway.domain.model.TenantId.DEFAULT
    private val now = Instant.fromEpochMilliseconds(1_000)

    private fun account() = User(
        id = UserId.random(),
        email = "u@example.com",
        emailVerified = true,
        displayName = null,
        status = UserStatus.ACTIVE,
        mfaRequired = false,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun unknownUserStillRunsAVerifyAgainstTheDummyHash() = runTest {
        every { hasher.verify(any(), any()) } returns false
        coEvery { users.findByEmail(any(), any()) } returns null
        val auth = authenticator()

        assertFailsWith<GatewayException.Unauthenticated> {
            auth.authenticate(tenant, "nobody@example.com", "correcthorsebattery".toCharArray())
        }
        // The equal-work path must exercise the real hasher against the dummy hash,
        // so an unknown account can't be told apart by response timing.
        verify(exactly = 1) { hasher.verify(any(), dummy) }
    }

    @Test
    fun knownUserWithGoodPasswordAuthenticates() = runTest {
        val user = account()
        coEvery { users.findByEmail(any(), "u@example.com") } returns user
        coEvery { credentials.findPasswordHash(any(), user.id) } returns "stored-hash"
        every { hasher.verify(any(), "stored-hash") } returns true
        every { hasher.needsRehash("stored-hash") } returns false
        val auth = authenticator()

        assertEquals(user.id, auth.authenticate(tenant, "u@example.com", "correcthorsebattery".toCharArray()).id)
        verify(exactly = 0) { hasher.verify(any(), dummy) }
    }

    @Test
    fun knownUserWithBadPasswordIsUnauthorized() = runTest {
        val user = account()
        coEvery { users.findByEmail(any(), "u@example.com") } returns user
        coEvery { credentials.findPasswordHash(any(), user.id) } returns "stored-hash"
        every { hasher.verify(any(), "stored-hash") } returns false
        val auth = authenticator()

        assertFailsWith<GatewayException.Unauthenticated> {
            auth.authenticate(tenant, "u@example.com", "wrongwrongwrong".toCharArray())
        }
    }
}
