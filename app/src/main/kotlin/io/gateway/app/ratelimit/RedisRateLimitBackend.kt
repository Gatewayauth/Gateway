package io.gateway.app.ratelimit

import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Redis fixed-window backend: `INCR` the key, and on the first hit arm a `PEXPIRE`
 * for the window. Shared across instances so the limit is deployment-wide.
 */
class RedisRateLimitBackend(private val redis: RedisCommands<String, String>) : RateLimitBackend {
    override suspend fun hit(key: String, window: Duration): Long = withContext(Dispatchers.IO) {
        val count = redis.incr(key)
        if (count == 1L) redis.pexpire(key, window.inWholeMilliseconds)
        count
    }
}
