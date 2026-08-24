package io.gateway.common

import java.security.MessageDigest

/**
 * SHA-256 hashing for non-password secrets that must be stored irreversibly but
 * verified by exact match (auth codes, refresh tokens, recovery codes, PKCE S256).
 * Passwords must NOT use this — use the Argon2 password hasher instead.
 */
object Sha256 {
    fun hash(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hash(value: String): ByteArray = hash(value.toByteArray(Charsets.UTF_8))

    /** Hash to a URL-safe string suitable for a DB lookup key. */
    fun hashToBase64Url(value: String): String = Base64Url.encode(hash(value))
}
