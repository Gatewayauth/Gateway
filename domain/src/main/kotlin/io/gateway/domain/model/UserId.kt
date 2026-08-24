package io.gateway.domain.model

import java.util.UUID

/**
 * Strongly-typed identifiers. Using distinct value classes prevents accidentally
 * passing a UserId where a SessionId is expected. All wrap a UUID.
 */
@JvmInline
value class UserId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): UserId = UserId(UUID.randomUUID())
        fun parse(s: String): UserId = UserId(UUID.fromString(s))
    }
}
