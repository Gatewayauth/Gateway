package io.gateway.domain.auth

/**
 * Password hashing SPI. Implementations must use a memory-hard algorithm
 * (Argon2id) with per-hash salt. [needsRehash] lets the app transparently
 * upgrade parameters on successful login.
 */
interface PasswordHasher {
    fun hash(password: CharArray): String

    fun verify(password: CharArray, encodedHash: String): Boolean

    fun needsRehash(encodedHash: String): Boolean
}
