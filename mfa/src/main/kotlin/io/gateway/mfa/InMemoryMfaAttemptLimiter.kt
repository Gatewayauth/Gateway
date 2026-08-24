package io.gateway.mfa

import io.gateway.common.GatewayException
import io.gateway.domain.model.UserId
import io.gateway.domain.time.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory [MfaAttemptLimiter]. After [maxAttempts] consecutive failures a user is
 * locked out for [lockout]. State is local to this instance — single-instance only,
 * the same assumption the in-process rate limiter makes.
 */
class InMemoryMfaAttemptLimiter(
    private val clock: Clock,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val lockout: Duration = DEFAULT_LOCKOUT,
) : MfaAttemptLimiter {
    private data class State(val failures: Int, val lockedUntil: Instant?)

    private val states = ConcurrentHashMap<String, State>()

    override suspend fun assertNotLocked(userId: UserId) {
        val until = states[userId.value.toString()]?.lockedUntil ?: return
        if (clock.now() < until) {
            throw GatewayException.RateLimited("Too many failed codes. Try again later.")
        }
    }

    override suspend fun recordFailure(userId: UserId) {
        states.compute(userId.value.toString()) { _, prev ->
            val now = clock.now()
            val carried = prev?.takeIf { it.lockedUntil == null || now < it.lockedUntil }
            val failures = (carried?.failures ?: 0) + 1
            if (failures >= maxAttempts) State(failures, now.plus(lockout)) else State(failures, null)
        }
    }

    override suspend fun reset(userId: UserId) {
        states.remove(userId.value.toString())
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        val DEFAULT_LOCKOUT = 15.minutes
    }
}
