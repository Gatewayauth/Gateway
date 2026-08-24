package io.gateway.common

import java.security.SecureRandom

/**
 * Cryptographically strong random token/identifier generation.
 * All security-sensitive secrets (session ids, auth codes, refresh tokens,
 * client secrets, recovery codes) must originate here.
 */
object RandomTokens {
    private val secureRandom = SecureRandom()

    /** Default entropy for opaque secrets: 256 bits. */
    const val DEFAULT_BYTES: Int = 32

    fun bytes(count: Int = DEFAULT_BYTES): ByteArray {
        val out = ByteArray(count)
        secureRandom.nextBytes(out)
        return out
    }

    /** URL-safe opaque token with [count] bytes of entropy. */
    fun urlSafe(count: Int = DEFAULT_BYTES): String = Base64Url.encode(bytes(count))

    /** Numeric code of [digits] length, e.g. for TOTP fallbacks / email OTP. */
    fun numeric(digits: Int): String {
        val sb = StringBuilder(digits)
        repeat(digits) { sb.append(secureRandom.nextInt(DECIMAL_BASE)) }
        return sb.toString()
    }

    private const val DECIMAL_BASE = 10
}
