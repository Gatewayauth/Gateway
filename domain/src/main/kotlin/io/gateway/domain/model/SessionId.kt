package io.gateway.domain.model

import java.util.UUID

/** Server-side session primary key (never sent to the client verbatim). */
@JvmInline
value class SessionId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): SessionId = SessionId(UUID.randomUUID())
    }
}
