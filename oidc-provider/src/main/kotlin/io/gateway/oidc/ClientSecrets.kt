package io.gateway.oidc

import io.gateway.common.ConstantTime
import io.gateway.common.Sha256

/**
 * Client secret hashing/verification. Secrets are high-entropy random strings, so
 * a single SHA-256 (constant-time compared) is sufficient — unlike user passwords,
 * which require Argon2.
 */
object ClientSecrets {
    fun hash(secret: String): String = Sha256.hashToBase64Url(secret)

    fun verify(secret: String, storedHash: String): Boolean =
        ConstantTime.equals(Sha256.hashToBase64Url(secret), storedHash)
}
