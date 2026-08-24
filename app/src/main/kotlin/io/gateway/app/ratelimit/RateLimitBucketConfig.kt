package io.gateway.app.ratelimit

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Config for the [RateLimitBucket] plugin: a named per-IP fixed-window limit over [backend]. */
class RateLimitBucketConfig {
    var name: String = "default"
    var limit: Int = 60
    var window: Duration = 60.seconds
    lateinit var backend: RateLimitBackend
}
