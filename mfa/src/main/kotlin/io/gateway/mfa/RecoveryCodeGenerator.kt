package io.gateway.mfa

import io.gateway.common.RandomTokens
import io.gateway.common.Sha256

/**
 * Generates one-time MFA recovery codes. Only the SHA-256 hash of each code is
 * persisted; the plaintext set is shown to the user exactly once at generation.
 */
object RecoveryCodeGenerator {
    const val DEFAULT_COUNT = 10
    private const val CODE_BYTES = 8

    /** Returns plaintext codes (to display) paired with their storage hashes. */
    fun generate(count: Int = DEFAULT_COUNT): List<RecoveryCode> =
        (0 until count).map {
            val plaintext = RandomTokens.urlSafe(CODE_BYTES)
            RecoveryCode(plaintext = plaintext, hash = Sha256.hashToBase64Url(plaintext))
        }
}
