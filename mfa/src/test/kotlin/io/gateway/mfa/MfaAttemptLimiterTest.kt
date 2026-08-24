package io.gateway.mfa

import io.gateway.common.GatewayException
import io.gateway.domain.model.UserId
import io.gateway.domain.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class MfaAttemptLimiterTest {

    private var nowMs = 1_000_000L
    private val clock = Clock { Instant.fromEpochMilliseconds(nowMs) }
    private val limiter = InMemoryMfaAttemptLimiter(clock, maxAttempts = 3, lockout = 10.minutes)
    private val user = UserId.random()

    @Test
    fun locksOutAfterMaxFailures() = runTest {
        repeat(3) { limiter.recordFailure(user) }
        assertFailsWith<GatewayException.RateLimited> { limiter.assertNotLocked(user) }
    }

    @Test
    fun belowThresholdIsNotLocked() = runTest {
        repeat(2) { limiter.recordFailure(user) }
        limiter.assertNotLocked(user) // does not throw
    }

    @Test
    fun successResetsCounter() = runTest {
        repeat(2) { limiter.recordFailure(user) }
        limiter.reset(user)
        repeat(2) { limiter.recordFailure(user) }
        limiter.assertNotLocked(user) // still under threshold after reset
    }

    @Test
    fun lockoutExpiresAfterWindow() = runTest {
        repeat(3) { limiter.recordFailure(user) }
        assertFailsWith<GatewayException.RateLimited> { limiter.assertNotLocked(user) }
        nowMs += 11.minutes.inWholeMilliseconds
        limiter.assertNotLocked(user) // cooldown elapsed
    }
}
