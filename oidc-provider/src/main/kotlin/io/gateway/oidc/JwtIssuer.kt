package io.gateway.oidc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.time.Clock
import java.util.Date

/**
 * Mints signed (RS256) OIDC ID tokens and JWT access tokens. Claims follow the OIDC
 * core spec; `iss` is the per-tenant [issuer] and tokens are signed with that tenant's
 * key.
 */
class JwtIssuer(
    private val config: OidcConfig,
    private val keys: SigningKeyManager,
    private val clock: Clock,
) {
    fun issueIdToken(
        tenantId: TenantId,
        issuer: String,
        user: User,
        audience: String,
        nonce: String?,
        authTime: Long,
    ): String {
        val now = clock.now()
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(user.id.toString())
            .audience(audience)
            .issueTime(Date(now.toEpochMilliseconds()))
            .expirationTime(Date(now.plus(config.idTokenTtl).toEpochMilliseconds()))
            .claim("email", user.email)
            .claim("email_verified", user.emailVerified)
            .claim("auth_time", authTime)
            .apply {
                user.displayName?.let { claim("name", it) }
                nonce?.let { claim("nonce", it) }
            }
            .build()
        return sign(tenantId, claims)
    }

    fun issueAccessToken(
        tenantId: TenantId,
        issuer: String,
        subject: String,
        audience: String,
        scopes: Set<String>,
    ): String {
        val now = clock.now()
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .audience(audience)
            .issueTime(Date(now.toEpochMilliseconds()))
            .expirationTime(Date(now.plus(config.accessTokenTtl).toEpochMilliseconds()))
            .claim("scope", scopes.joinToString(" "))
            .build()
        return sign(tenantId, claims)
    }

    private fun sign(tenantId: TenantId, claims: JWTClaimsSet): String {
        val key = keys.current(tenantId)
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(key.keyId)
            .type(com.nimbusds.jose.JOSEObjectType.JWT)
            .build()
        val jwt = SignedJWT(header, claims)
        jwt.sign(RSASSASigner(key.jwk))
        return jwt.serialize()
    }
}
