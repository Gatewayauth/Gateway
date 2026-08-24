package io.gateway.oidc

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.ConsentRepository
import io.gateway.domain.repository.OAuthClientRepository

/** Records user consent for a client, validating the scopes against the client. */
class ConsentService(
    private val clients: OAuthClientRepository,
    private val consents: ConsentRepository,
) {
    suspend fun grant(tenantId: TenantId, userId: UserId, clientId: String, scopes: Set<String>) {
        val client = clients.findById(tenantId, ClientId(clientId))
            ?: throw OAuthException.invalidClient("Unknown client.")
        val unknown = scopes - client.allowedScopes
        if (unknown.isNotEmpty()) {
            throw OAuthException.invalidScope("Unsupported scope(s): ${unknown.joinToString(" ")}")
        }
        consents.grant(tenantId, userId, client.id, scopes)
    }
}
