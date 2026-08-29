package io.gateway.app.redis

import io.gateway.common.GatewayException
import io.gateway.domain.model.UserId
import io.gateway.mfa.MfaAttemptLimiter
import io.lettuce.core.SetArgs
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Redis-backed [MfaAttemptLimiter]: a per-user failure counter (`INCR` + TTL) and a
 * lock key set once the threshold is reached. Shared across instances.
 */
class RedisMfaAttemptLimiter(
    private val redis: RedisCommands<String, String>,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val window: Duration = DEFAULT_WINDOW,
    private val lockout: Duration = DEFAULT_LOCKOUT,
) : MfaAttemptLimiter {

    override suspend fun assertNotLocked(userId: UserId): Unit = withContext(Dispatchers.IO) {
        if (redis.exists(lockKey(userId)) > 0) {
            throw GatewayException.RateLimited("Too many failed codes. Try again later.")
        }
    }

    override suspend fun recordFailure(userId: UserId): Unit = withContext(Dispatchers.IO) {
        val count = redis.incr(failKey(userId))
        if (count == 1L) redis.pexpire(failKey(userId), window.inWholeMilliseconds)
        if (count >= maxAttempts) {
            redis.set(lockKey(userId), "1", SetArgs.Builder.px(lockout.inWholeMilliseconds))
            redis.del(failKey(userId))
        }
    }

    override suspend fun reset(userId: UserId): Unit = withContext(Dispatchers.IO) {
        redis.del(failKey(userId), lockKey(userId))
    }

    private fun failKey(userId: UserId) = "mfa:fail:${userId.value}"
    private fun lockKey(userId: UserId) = "mfa:lock:${userId.value}"

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        val DEFAULT_WINDOW = 15.minutes
        val DEFAULT_LOCKOUT = 15.minutes
    }
}
