package io.gateway.app

import io.gateway.domain.time.Clock
import kotlinx.datetime.Instant

/** Production [Clock] backed by the system UTC clock. */
class SystemClock : Clock {
    override fun now(): Instant = kotlinx.datetime.Clock.System.now()
}
