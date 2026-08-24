package io.gateway.oidc

import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator

/**
 * Holds one RSA signing key pair. Can be generated fresh or restored from its
 * stored JWK JSON (which includes the private key and is kept encrypted at rest).
 */
class RsaSigningKey private constructor(val jwk: RSAKey) {

    val keyId: String get() = jwk.keyID

    /** Public-only JWK, safe to publish at the JWKS endpoint. */
    fun publicJwk(): RSAKey = jwk.toPublicJWK()

    /** Full JWK JSON including private key material — encrypt before persisting. */
    fun privateJwkJson(): String = jwk.toJSONString()

    companion object {
        private const val KEY_SIZE = 2048

        fun generate(keyId: String): RsaSigningKey =
            RsaSigningKey(RSAKeyGenerator(KEY_SIZE).keyID(keyId).generate())

        fun fromJwkJson(json: String): RsaSigningKey = RsaSigningKey(RSAKey.parse(json))
    }
}
