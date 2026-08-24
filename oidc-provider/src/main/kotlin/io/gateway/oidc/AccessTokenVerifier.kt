package io.gateway.oidc

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import io.gateway.domain.model.TenantId
import io.gateway.domain.time.Clock
import java.util.Date

/**
 * Verifies a Gateway-issued JWT access token against a tenant's signing keys: RS256
 * signature, the tenant [issuer], and expiry. Returns the subject and scopes.
 */
class AccessTokenVerifier(
    private val keys: SigningKeyManager,
    private val clock: Clock,
) {
    fun verify(tenantId: TenantId, issuer: String, token: String): AccessTokenClaims {
        val jwt = runCatching { SignedJWT.parse(token) }
            .getOrElse { throw OAuthException.invalidGrant("Malformed access token.") }

        val publicKey = keys.publicKeyFor(tenantId, jwt.header.keyID)
            ?: throw OAuthException.invalidGrant("Unknown signing key.")
        if (!jwt.verify(RSASSAVerifier(publicKey))) throw OAuthException.invalidGrant("Bad token signature.")

        val claims = jwt.jwtClaimsSet
        if (claims.issuer != issuer) throw OAuthException.invalidGrant("Wrong token issuer.")
        val expiry = claims.expirationTime ?: throw OAuthException.invalidGrant("Token has no expiry.")
        if (expiry.before(Date(clock.now().toEpochMilliseconds()))) {
            throw OAuthException.invalidGrant("Access token expired.")
        }

        val scopes = (claims.getStringClaim("scope") ?: "")
            .split(" ").filter { it.isNotBlank() }.toSet()
        return AccessTokenClaims(subject = claims.subject, scopes = scopes)
    }
}
