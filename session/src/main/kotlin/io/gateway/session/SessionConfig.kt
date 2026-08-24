package io.gateway.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Session lifetime settings. */
data class SessionConfig(
    val ttl: Duration = 12.hours,
    val cookieName: String = "gw_session",
    // Minimum age of `lastSeenAt` before a resolve writes a new one. Avoids a DB
    // write on every authenticated request while keeping activity roughly current.
    val touchInterval: Duration = 5.minutes,
)
