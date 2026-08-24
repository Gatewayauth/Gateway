package io.gateway.oidc

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.OAuthClient
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.OAuthClientRepository

/**
 * Authenticates a client at the token endpoint. Confidential clients must present
 * a valid secret (Basic or POST body); public clients authenticate via PKCE alone.
 */
class ClientAuthenticator(private val clients: OAuthClientRepository) {

    suspend fun authenticate(tenantId: TenantId, clientId: String, clientSecret: String?): OAuthClient {
        val client = clients.findById(tenantId, ClientId(clientId))
            ?: throw OAuthException.invalidClient("Unknown client.")

        if (!client.public) {
            val hash = client.secretHash
                ?: throw OAuthException.invalidClient("Client is misconfigured (no secret).")
            if (clientSecret == null || !ClientSecrets.verify(clientSecret, hash)) {
                throw OAuthException.invalidClient("Invalid client credentials.")
            }
        }
        return client
    }
}
