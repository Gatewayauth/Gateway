package io.gateway.common

import java.security.MessageDigest

/** Timing-attack-resistant comparisons for secrets and tokens. */
object ConstantTime {
    fun equals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun equals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
