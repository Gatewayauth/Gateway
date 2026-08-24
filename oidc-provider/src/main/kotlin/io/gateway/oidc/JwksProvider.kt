package io.gateway.oidc

import com.nimbusds.jose.jwk.JWKSet
import io.gateway.domain.model.TenantId

/** Publishes a tenant's currently valid public signing keys as a JWKS JSON object. */
class JwksProvider(private val keys: SigningKeyManager) {

    /** JWKS as a JSON-ready map (public keys only, including retired-but-valid keys). */
    fun jwks(tenantId: TenantId): Map<String, Any> = JWKSet(keys.publicJwks(tenantId)).toJSONObject(true)
}
