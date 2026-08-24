package io.gateway.app

import io.gateway.app.redis.NoopDistributedLock
import io.gateway.audit.AuditLogger
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.time.Clock
import io.gateway.oidc.SigningKeyManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class KeyRotationSchedulerTest {

    private val keys = mockk<SigningKeyManager>()
    private val tenants = mockk<TenantRepository>(relaxed = true)
    private val audit = mockk<AuditLogger>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(30L * 24 * 60 * 60 * 1000)
    private val clock = Clock { now }
    private val tenant = TenantId.DEFAULT

    @Test
    fun dueWhenKeyOlderThanInterval() {
        every { keys.activeKeyCreatedAt(tenant) } returns now.minus(10.days)
        assertTrue(scheduler(rotationInterval = 7.days).isDue(now, tenant))
    }

    @Test
    fun notDueWhenKeyYoungerThanInterval() {
        every { keys.activeKeyCreatedAt(tenant) } returns now.minus(2.days)
        assertFalse(scheduler(rotationInterval = 7.days).isDue(now, tenant))
    }

    @Test
    fun disabledSchedulerDoesNotStart() {
        assertNull(scheduler(rotationInterval = 0.days).start(TestScope()))
    }

    private fun scheduler(rotationInterval: kotlin.time.Duration) =
        KeyRotationScheduler(keys, tenants, audit, clock, rotationInterval, NoopDistributedLock())
}
