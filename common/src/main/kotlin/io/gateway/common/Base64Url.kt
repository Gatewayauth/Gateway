package io.gateway.common

import java.util.Base64

/** URL-safe, unpadded Base64 encoding used for tokens, PKCE, and JWK material. */
object Base64Url {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(value: String): ByteArray = decoder.decode(value)
}
