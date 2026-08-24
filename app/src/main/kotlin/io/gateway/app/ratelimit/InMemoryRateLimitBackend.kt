package io.gateway.app.ratelimit

import io.gateway.domain.time.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/** Per-instance fixed-window rate-limit backend (single-instance / tests). */
class InMemoryRateLimitBackend(private val clock: Clock) : RateLimitBackend {
    private data class Window(val count: Long, val resetAt: Instant)

    private val windows = ConcurrentHashMap<String, Window>()

    override suspend fun hit(key: String, window: Duration): Long {
        val now = clock.now()
        return windows.compute(key) { _, prev ->
            if (prev == null || now >= prev.resetAt) Window(1, now.plus(window)) else prev.copy(count = prev.count + 1)
        }!!.count
    }
}
