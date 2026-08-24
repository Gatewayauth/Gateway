package io.gateway.authexternal

import io.gateway.common.GatewayException
import io.gateway.domain.model.ExternalIdentity
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.repository.ExternalIdentityRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountLinkingServiceTest {

    private val users = mockk<UserRepository>(relaxed = true)
    private val identities = mockk<ExternalIdentityRepository>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val clock = Clock { now }
    private val service = AccountLinkingService(users, identities, clock)

    private fun user(email: String) = User(
        id = UserId.random(),
        email = email,
        emailVerified = true,
        displayName = null,
        status = UserStatus.ACTIVE,
        mfaRequired = false,
        createdAt = now,
        updatedAt = now,
    )

    private fun profile(email: String?, verified: Boolean) = ExternalProfile(
        provider = "google",
        subject = "sub-123",
        email = email,
        emailVerified = verified,
        displayName = "Ext User",
    )

    private val tenant = io.gateway.domain.model.TenantId.DEFAULT

    @Test
    fun knownSubjectLogsIntoLinkedUser() = runTest {
        val existing = user("linked@example.com")
        coEvery { identities.findByProviderSubject(any(), "google", "sub-123") } returns ExternalIdentity(
            id = "id", userId = existing.id, provider = "google", subject = "sub-123",
            email = "linked@example.com", createdAt = now,
        )
        coEvery { users.findById(any(), existing.id) } returns existing

        assertEquals(existing.id, service.resolve(tenant, profile("linked@example.com", true)).id)
        coVerify(exactly = 0) { identities.insert(any(), any()) }
    }

    @Test
    fun verifiedEmailLinksToExistingLocalAccount() = runTest {
        coEvery { identities.findByProviderSubject(any(), any(), any()) } returns null
        val existing = user("me@example.com")
        coEvery { users.findByEmail(any(), "me@example.com") } returns existing

        val resolved = service.resolve(tenant, profile("Me@Example.com", verified = true))

        assertEquals(existing.id, resolved.id)
        coVerify(exactly = 1) { identities.insert(any(), any()) }
        coVerify(exactly = 0) { users.insert(any(), any()) }
    }

    @Test
    fun unverifiedEmailToExistingAccountIsRefused() = runTest {
        coEvery { identities.findByProviderSubject(any(), any(), any()) } returns null
        coEvery { users.findByEmail(any(), "me@example.com") } returns user("me@example.com")

        assertFailsWith<GatewayException.Conflict> {
            service.resolve(tenant, profile("Me@Example.com", verified = false))
        }
        coVerify(exactly = 0) { identities.insert(any(), any()) }
    }

    @Test
    fun newProfileCreatesAccountWithNormalizedEmail() = runTest {
        coEvery { identities.findByProviderSubject(any(), any(), any()) } returns null
        coEvery { users.findByEmail(any(), "new@example.com") } returns null
        val inserted = slot<User>()
        coEvery { users.insert(any(), capture(inserted)) } answers { inserted.captured }

        service.resolve(tenant, profile("New@Example.com", verified = true))

        assertEquals("new@example.com", inserted.captured.email)
        coVerify(exactly = 1) { identities.insert(any(), any()) }
    }
}
