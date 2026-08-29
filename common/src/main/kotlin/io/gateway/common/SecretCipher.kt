package io.gateway.common

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated symmetric encryption (AES-256-GCM) for secrets that must be
 * recoverable, e.g. TOTP shared secrets. Output is `BASE64URL(iv || ciphertext||tag)`.
 * The key is a 32-byte application secret supplied via configuration.
 */
class SecretCipher(key: ByteArray) {

    init {
        require(key.size == KEY_BYTES) { "Encryption key must be $KEY_BYTES bytes (got ${key.size})." }
    }

    private val keySpec = SecretKeySpec(key, "AES")

    fun encrypt(plaintext: String): String {
        val iv = RandomTokens.bytes(IV_BYTES) // fresh SecureRandom 96-bit nonce per message
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection — IV is a unique per-message SecureRandom nonce, prepended to the ciphertext; no key+IV reuse.
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64Url.encode(iv + ciphertext)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64Url.decode(encoded)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection — decrypt reuses the per-message IV recovered from the ciphertext, which is correct/required.
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** Decode a base64url or base64 32-byte key from configuration. */
        fun keyFromBase64(value: String): ByteArray =
            runCatching { Base64Url.decode(value) }.getOrElse { java.util.Base64.getDecoder().decode(value) }
    }
}
