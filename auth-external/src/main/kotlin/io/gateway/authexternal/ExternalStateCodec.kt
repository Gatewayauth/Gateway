package io.gateway.authexternal

import io.gateway.common.Base64Url
import io.gateway.common.Hmac
import io.gateway.common.RandomTokens
import io.gateway.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Encodes the CSRF `state` + PKCE `code_verifier` for an external login into a
 * signed, short-lived value stored in an HttpOnly cookie. On callback the returned
 * `state` is checked against the cookie, defeating CSRF and login-fixation.
 */
class ExternalStateCodec(key: ByteArray, private val clock: Clock, private val ttl: Duration = DEFAULT_TTL) {

    private val hmac = Hmac(key)

    fun issue(provider: String): IssuedState {
        val state = RandomTokens.urlSafe(STATE_BYTES)
        val verifier = RandomTokens.urlSafe(VERIFIER_BYTES)
        val payload = listOf(provider, state, verifier, expiry()).joinToString(DELIMITER)
        val cookie = Base64Url.encode(payload.toByteArray()) + "." + hmac.signToBase64Url(payload)
        return IssuedState(state = state, codeVerifier = verifier, cookieValue = cookie)
    }

    fun verify(cookieValue: String, returnedState: String): VerifiedState? {
        val parts = cookieValue.split(".")
        if (parts.size != 2) return null
        val payload = runCatching { String(Base64Url.decode(parts[0])) }.getOrNull() ?: return null
        if (!hmac.verify(payload, parts[1])) return null

        val fields = payload.split(DELIMITER)
        if (fields.size != FIELD_COUNT) return null
        val provider = fields[0]
        val state = fields[1]
        val verifier = fields[2]
        val expiresAt = fields[3].toLongOrNull() ?: return null
        if (state != returnedState) return null
        if (expiresAt <= clock.now().toEpochMilliseconds()) return null
        return VerifiedState(provider = provider, codeVerifier = verifier)
    }

    private fun expiry(): String = clock.now().plus(ttl).toEpochMilliseconds().toString()

    private companion object {
        val DEFAULT_TTL = 10.minutes
        const val STATE_BYTES = 24
        const val VERIFIER_BYTES = 32
        const val DELIMITER = "|"
        const val FIELD_COUNT = 4
    }
}
