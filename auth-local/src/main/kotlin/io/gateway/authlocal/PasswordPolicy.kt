package io.gateway.authlocal

import io.gateway.common.GatewayException

/** Minimum password strength policy. Rejects short or trivially weak passwords. */
object PasswordPolicy {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 256

    fun validate(password: CharArray) {
        if (password.size < MIN_LENGTH) {
            throw GatewayException.Validation("Password must be at least $MIN_LENGTH characters.")
        }
        if (password.size > MAX_LENGTH) {
            throw GatewayException.Validation("Password must be at most $MAX_LENGTH characters.")
        }
    }
}
