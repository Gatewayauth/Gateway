package io.gateway.common

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA256 for short-lived signed tokens (e.g. MFA challenge tokens). */
class Hmac(key: ByteArray) {

    private val keySpec = SecretKeySpec(key, ALGORITHM)

    fun sign(data: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    fun signToBase64Url(data: String): String = Base64Url.encode(sign(data.toByteArray(Charsets.UTF_8)))

    fun verify(data: String, expectedBase64Url: String): Boolean =
        ConstantTime.equals(signToBase64Url(data), expectedBase64Url)

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}
