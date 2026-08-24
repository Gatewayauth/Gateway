package io.gateway.app.redis

import io.gateway.app.ratelimit.RedisRateLimitBackend
import io.gateway.common.GatewayException
import io.gateway.domain.model.UserId
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisBackendsTest {

    private val redis = mockk<RedisCommands<String, String>>(relaxed = true)

    @Test
    fun rateLimitArmsExpiryOnlyOnFirstHit() = runTest {
        every { redis.incr("k") } returnsMany listOf(1L, 2L)
        val backend = RedisRateLimitBackend(redis)

        assertEquals(1L, backend.hit("k", 60.seconds))
        assertEquals(2L, backend.hit("k", 60.seconds))
        verify(exactly = 1) { redis.pexpire("k", 60_000) }
    }

    @Test
    fun mfaLimiterLocksAtThresholdAndClearsCounter() = runTest {
        val user = UserId.random()
        val failKey = "mfa:fail:${user.value}"
        val lockKey = "mfa:lock:${user.value}"
        every { redis.incr(failKey) } returnsMany listOf(1L, 2L, 3L)
        val limiter = RedisMfaAttemptLimiter(redis, maxAttempts = 3, window = 15.minutes, lockout = 15.minutes)

        limiter.recordFailure(user)
        limiter.recordFailure(user)
        limiter.recordFailure(user)

        verify(exactly = 1) { redis.pexpire(failKey, any<Long>()) } // only on the first failure
        verify(exactly = 1) { redis.psetex(lockKey, any<Long>(), "1") } // locked at the threshold
        verify(exactly = 1) { redis.del(failKey) }
    }

    @Test
    fun mfaLimiterThrowsWhenLockKeyPresent() = runTest {
        val user = UserId.random()
        every { redis.exists("mfa:lock:${user.value}") } returns 1L
        val limiter = RedisMfaAttemptLimiter(redis)

        assertFailsWith<GatewayException.RateLimited> { limiter.assertNotLocked(user) }
    }

    @Test
    fun distributedLockRunsBlockWhenAcquiredAndReleasesOwnToken() = runTest {
        val token = slot<String>()
        every { redis.set(eq("gw:lock"), capture(token), any()) } returns "OK"
        every { redis.get("gw:lock") } answers { token.captured }
        val lock = RedisDistributedLock(redis)

        var ran = false
        val result = lock.withLock("gw:lock", 30.seconds) {
            ran = true
            42
        }

        assertEquals(42, result)
        assertTrue(ran)
        verify(exactly = 1) { redis.del("gw:lock") }
    }

    @Test
    fun distributedLockSkipsBlockWhenNotAcquired() = runTest {
        every { redis.set(any(), any(), any()) } returns null
        val lock = RedisDistributedLock(redis)

        val result = lock.withLock<Unit>("gw:lock", 30.seconds) { fail("block must not run without the lock") }

        assertNull(result)
        verify(exactly = 0) { redis.del(any()) }
    }
}
