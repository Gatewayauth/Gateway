package io.gateway.domain.time

import kotlinx.datetime.Instant

/**
 * Time source abstraction. Injecting this (rather than calling Instant.now())
 * keeps token-expiry and session logic deterministically testable.
 */
fun interface Clock {
    fun now(): Instant
}
