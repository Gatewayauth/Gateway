package io.gateway.oidc

import io.gateway.common.RandomTokens
import io.gateway.common.Sha256
import io.gateway.domain.model.OAuthClient
import io.gateway.domain.model.RefreshTokenRecord
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.AuthorizationCodeRepository
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.domain.repository.RefreshTokenRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock

/**
 * The token endpoint core. Handles the `authorization_code` and `refresh_token`
 * grants, minting JWT access/ID tokens and rotating refresh tokens with reuse
 * detection. The client is already authenticated by the caller.
 */
class TokenService(
    private val codes: AuthorizationCodeRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val users: UserRepository,
    private val roles: RbacRoleRepository,
    private val jwtIssuer: JwtIssuer,
    private val config: OidcConfig,
    private val clock: Clock,
) {
    private companion object {
        const val SCOPE_OPENID = "openid"
        const val SCOPE_OFFLINE = "offline_access"
        const val SCOPE_ROLES = "roles"
    }

    /** Custom RBAC role slugs for the user, only when the `roles` scope was granted. */
    private suspend fun roleSlugs(tenantId: TenantId, user: User, scopes: Set<String>): List<String> =
        if (SCOPE_ROLES in scopes) roles.listForUser(tenantId, user.id).map { it.slug } else emptyList()

    suspend fun authorizationCodeGrant(
        tenantId: TenantId,
        issuer: String,
        client: OAuthClient,
        code: String,
        redirectUri: String,
        codeVerifier: String?,
    ): TokenResult {
        val grant = codes.consume(tenantId, Sha256.hashToBase64Url(code), clock.now())
            ?: throw OAuthException.invalidGrant("Authorization code is invalid or expired.")

        if (grant.clientId != client.id) throw OAuthException.invalidGrant("Code was issued to another client.")
        if (grant.redirectUri != redirectUri) throw OAuthException.invalidGrant("redirect_uri mismatch.")

        verifyPkce(grant.codeChallenge, grant.codeChallengeMethod, codeVerifier)

        val user = users.findById(tenantId, grant.userId)
            ?: throw OAuthException.invalidGrant("User no longer exists.")

        val idToken = if (SCOPE_OPENID in grant.scopes) {
            jwtIssuer.issueIdToken(
                tenantId, issuer, user, client.id.value, grant.nonce, grant.authTime.epochSeconds,
                roles = roleSlugs(tenantId, user, grant.scopes),
            )
        } else {
            null
        }
        val refresh = if (SCOPE_OFFLINE in grant.scopes) {
            issueRefresh(tenantId, client, user.id, grant.scopes, familyId = RandomTokens.urlSafe())
        } else {
            null
        }
        return buildResult(tenantId, issuer, user, client, grant.scopes, idToken, refresh)
    }

    suspend fun refreshTokenGrant(
        tenantId: TenantId,
        issuer: String,
        client: OAuthClient,
        refreshToken: String,
    ): TokenResult {
        val now = clock.now()
        val hash = Sha256.hashToBase64Url(refreshToken)
        val record = refreshTokens.findByHash(tenantId, hash)
            ?: throw OAuthException.invalidGrant("Unknown refresh token.")

        if (record.clientId != client.id) throw OAuthException.invalidGrant("Refresh token belongs to another client.")

        // Reuse detection: a token that was already rotated must never be replayed.
        if (record.rotatedAt != null) {
            refreshTokens.revokeFamily(tenantId, record.familyId, now)
            throw OAuthException.invalidGrant("Refresh token reuse detected; the token family was revoked.")
        }
        if (!record.isUsable(now)) throw OAuthException.invalidGrant("Refresh token is expired or revoked.")

        // Claim the rotation atomically. Losing the race means a concurrent refresh
        // already rotated this exact token — treat it as reuse and burn the family.
        if (!refreshTokens.markRotated(tenantId, hash, now)) {
            refreshTokens.revokeFamily(tenantId, record.familyId, now)
            throw OAuthException.invalidGrant("Refresh token reuse detected; the token family was revoked.")
        }
        val newRefresh = issueRefresh(tenantId, client, record.userId, record.scopes, record.familyId)

        val user = users.findById(tenantId, record.userId)
            ?: throw OAuthException.invalidGrant("User no longer exists.")
        val idToken = if (SCOPE_OPENID in record.scopes) {
            jwtIssuer.issueIdToken(
                tenantId, issuer, user, client.id.value, nonce = null, authTime = now.epochSeconds,
                roles = roleSlugs(tenantId, user, record.scopes),
            )
        } else {
            null
        }
        return buildResult(tenantId, issuer, user, client, record.scopes, idToken, newRefresh)
    }

    @Suppress("LongParameterList")
    private fun buildResult(
        tenantId: TenantId,
        issuer: String,
        user: User,
        client: OAuthClient,
        scopes: Set<String>,
        idToken: String?,
        refreshToken: String?,
    ): TokenResult {
        val accessToken = jwtIssuer.issueAccessToken(tenantId, issuer, user.id.toString(), client.id.value, scopes)
        return TokenResult(
            accessToken = accessToken,
            expiresInSeconds = config.accessTokenTtl.inWholeSeconds,
            scope = scopes.joinToString(" "),
            idToken = idToken,
            refreshToken = refreshToken,
        )
    }

    private suspend fun issueRefresh(
        tenantId: TenantId,
        client: OAuthClient,
        userId: UserId,
        scopes: Set<String>,
        familyId: String,
    ): String {
        val raw = RandomTokens.urlSafe()
        val now = clock.now()
        refreshTokens.insert(
            tenantId,
            RefreshTokenRecord(
                tokenHash = Sha256.hashToBase64Url(raw),
                familyId = familyId,
                clientId = client.id,
                userId = userId,
                scopes = scopes,
                issuedAt = now,
                expiresAt = now.plus(config.refreshTokenTtl),
                rotatedAt = null,
                revokedAt = null,
            ),
        )
        return raw
    }

    private fun verifyPkce(challenge: String?, method: String?, verifier: String?) {
        if (challenge == null) return
        if (verifier.isNullOrBlank()) throw OAuthException.invalidGrant("PKCE code_verifier is required.")
        if (!Pkce.verify(verifier, challenge, method)) throw OAuthException.invalidGrant("PKCE verification failed.")
    }
}
