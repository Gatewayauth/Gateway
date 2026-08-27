package io.gateway.oidc

import io.gateway.common.RandomTokens
import io.gateway.domain.model.ClientId
import io.gateway.domain.model.GrantType
import io.gateway.domain.model.OAuthClient
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.OAuthClientRepository
import io.gateway.domain.time.Clock

/** Registers OAuth2 relying parties. Confidential clients get a one-time secret. */
class ClientRegistrationService(
    private val clients: OAuthClientRepository,
    private val clock: Clock,
) {
    @Suppress("LongParameterList")
    suspend fun register(
        tenantId: TenantId,
        name: String,
        redirectUris: Set<String>,
        scopes: Set<String>,
        public: Boolean,
        requireConsent: Boolean = true,
        requiredRoles: Set<String> = emptySet(),
        clientId: String = RandomTokens.urlSafe(CLIENT_ID_BYTES),
    ): ClientRegistration {
        require(redirectUris.isNotEmpty()) { "At least one redirect URI is required." }

        val secret = if (public) null else RandomTokens.urlSafe()
        val client = OAuthClient(
            id = ClientId(clientId),
            name = name,
            public = public,
            secretHash = secret?.let { ClientSecrets.hash(it) },
            redirectUris = redirectUris,
            allowedScopes = scopes + "openid",
            grantTypes = setOf(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN),
            requirePkce = true,
            requireConsent = requireConsent,
            requiredRoles = requiredRoles,
            createdAt = clock.now(),
        )
        return ClientRegistration(clients.insert(tenantId, client), secret)
    }

    private companion object {
        const val CLIENT_ID_BYTES = 12
    }
}
