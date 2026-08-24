package io.gateway.app.ratelimit

import kotlin.time.Duration

/**
 * Fixed-window request counter behind the rate limiter. The in-memory impl is
 * per-instance (fine for a single instance); the Redis impl shares counts across
 * instances so the limit holds for the whole deployment.
 */
interface RateLimitBackend {
    /** Increment the counter for [key], (re)arming its [window] expiry; return the new count. */
    suspend fun hit(key: String, window: Duration): Long
}
