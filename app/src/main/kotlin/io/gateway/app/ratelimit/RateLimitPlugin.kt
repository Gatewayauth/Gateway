package io.gateway.app.ratelimit

import io.gateway.common.GatewayException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.origin
import kotlin.time.Duration

/**
 * Route-scoped per-IP rate limit. Install it inside a route group to cap every
 * endpoint under it; over-limit calls throw [GatewayException.RateLimited] (429).
 * The client IP comes from `origin.remoteHost`, which honours the forwarded-headers
 * plugin, so this keys on the real client behind the proxy.
 */
val RateLimitBucket = createRouteScopedPlugin("RateLimitBucket", ::RateLimitBucketConfig) {
    val name = pluginConfig.name
    val limit = pluginConfig.limit
    val window = pluginConfig.window
    val backend = pluginConfig.backend
    onCall { call ->
        if (backend.hit("rl:$name:${call.request.origin.remoteHost}", window) > limit) {
            throw GatewayException.RateLimited("Too many requests. Slow down and try again shortly.")
        }
    }
}

/**
 * Explicit per-IP limit for a single handler, layered on top of any route-scoped
 * bucket — used for the credential-guessing endpoints that need a tighter cap.
 */
suspend fun ApplicationCall.enforceRateLimit(
    backend: RateLimitBackend,
    name: String,
    limit: Int,
    window: Duration,
) {
    if (backend.hit("rl:$name:${request.origin.remoteHost}", window) > limit) {
        throw GatewayException.RateLimited("Too many requests. Slow down and try again shortly.")
    }
}
