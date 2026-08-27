package io.gateway.oidc

import io.gateway.common.RandomTokens
import io.gateway.common.Sha256
import io.gateway.domain.model.AuthorizationGrant
import io.gateway.domain.model.ClientId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.AuthorizationCodeRepository
import io.gateway.domain.repository.ConsentRepository
import io.gateway.domain.repository.OAuthClientRepository
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.domain.time.Clock
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Handles the OIDC authorization-code request. The caller must already have an
 * authenticated user (session); this service validates the client/redirect/scope/
 * PKCE, mints a single-use code, and returns the redirect Location.
 *
 * Client and redirect_uri errors throw [OAuthException] (the caller returns an
 * error WITHOUT redirecting, per spec, to avoid open-redirect abuse).
 */
class AuthorizationService(
    private val clients: OAuthClientRepository,
    private val codes: AuthorizationCodeRepository,
    private val consents: ConsentRepository,
    private val roles: RbacRoleRepository,
    private val config: OidcConfig,
    private val clock: Clock,
) {
    suspend fun authorize(tenantId: TenantId, request: AuthorizationRequest, userId: UserId): String {
        val client = clients.findById(tenantId, ClientId(request.clientId))
            ?: throw OAuthException.invalidClient("Unknown client.")
        if (!client.allowsRedirect(request.redirectUri)) {
            throw OAuthException.invalidRequest("redirect_uri is not registered for this client.")
        }
        if (request.responseType != "code") {
            throw OAuthException.invalidRequest("Only response_type=code is supported.")
        }

        val requested = request.scope.split(" ").filter { it.isNotBlank() }.toSet()
        if ("openid" !in requested) {
            throw OAuthException.invalidScope("The openid scope is required.")
        }
        val unknown = requested - client.allowedScopes
        if (unknown.isNotEmpty()) {
            throw OAuthException.invalidScope("Unsupported scope(s): ${unknown.joinToString(" ")}")
        }

        // Gateway-side access gate: if the client requires roles, the user must hold
        // at least one. Denied here (before consent) so the user never reaches the
        // relying party — they see the Gateway's own "no access" page instead.
        if (client.requiredRoles.isNotEmpty()) {
            val userSlugs = roles.listForUser(tenantId, userId).map { it.slug }.toSet()
            if (client.requiredRoles.intersect(userSlugs).isEmpty()) {
                throw OAuthException.accessDenied(
                    "You don't have access to ${client.name}. Ask an administrator to grant you the required role.",
                )
            }
        }

        if (!Pkce.isSupportedMethod(request.codeChallengeMethod)) {
            throw OAuthException.invalidRequest("Only the S256 PKCE method is supported.")
        }
        if ((client.requirePkce || client.public) && request.codeChallenge.isNullOrBlank()) {
            throw OAuthException.invalidRequest("PKCE code_challenge is required for this client.")
        }

        if (client.requireConsent && !consents.hasConsent(tenantId, userId, client.id, requested)) {
            throw ConsentRequiredException(client.id.value, client.name, requested)
        }

        val now = clock.now()
        val code = RandomTokens.urlSafe()
        codes.insert(
            tenantId,
            AuthorizationGrant(
                codeHash = Sha256.hashToBase64Url(code),
                clientId = client.id,
                userId = userId,
                redirectUri = request.redirectUri,
                scopes = requested,
                nonce = request.nonce,
                codeChallenge = request.codeChallenge,
                codeChallengeMethod = request.codeChallenge?.let { request.codeChallengeMethod ?: Pkce.METHOD_S256 },
                authTime = now,
                expiresAt = now.plus(config.authorizationCodeTtl),
                consumedAt = null,
            ),
        )
        return buildRedirect(request.redirectUri, code, request.state)
    }

    private fun buildRedirect(redirectUri: String, code: String, state: String?): String {
        val separator = if (redirectUri.contains('?')) '&' else '?'
        val params = buildString {
            append("code=").append(encode(code))
            if (state != null) append("&state=").append(encode(state))
        }
        return "$redirectUri$separator$params"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
