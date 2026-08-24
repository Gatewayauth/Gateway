package io.gateway.oidc

import io.gateway.common.Base64Url
import io.gateway.common.ConstantTime
import io.gateway.common.Sha256

/**
 * PKCE (RFC 7636) verification. Only the S256 method is supported — `plain` is
 * rejected as insecure. The stored challenge is `BASE64URL(SHA256(verifier))`.
 */
object Pkce {
    const val METHOD_S256 = "S256"

    fun isSupportedMethod(method: String?): Boolean = method == null || method == METHOD_S256

    fun verify(codeVerifier: String, storedChallenge: String, method: String?): Boolean {
        if (method != null && method != METHOD_S256) return false
        val computed = Base64Url.encode(Sha256.hash(codeVerifier))
        return ConstantTime.equals(computed, storedChallenge)
    }
}
