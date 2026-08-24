package io.gateway.session

import io.gateway.domain.model.Session

/**
 * Result of creating a session. [rawToken] is the only time the opaque cookie
 * value exists in plaintext — set it on the response cookie and never persist it.
 */
data class IssuedSession(
    val session: Session,
    val rawToken: String,
)
