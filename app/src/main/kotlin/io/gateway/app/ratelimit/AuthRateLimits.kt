package io.gateway.app.ratelimit

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Per-IP rate-limit buckets for the auth surface. */
object AuthRateLimits {
    val WINDOW: Duration = 60.seconds

    /** General bucket applied to every auth/MFA/external endpoint. */
    const val GENERAL_NAME = "auth"
    const val GENERAL_LIMIT = 60

    /** Tighter bucket layered on the credential-guessing endpoints (login/MFA/reset). */
    const val SENSITIVE_NAME = "auth-sensitive"
    const val SENSITIVE_LIMIT = 10
}
