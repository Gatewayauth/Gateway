package io.gateway.oidc

import io.gateway.domain.model.OAuthClient

/**
 * Result of registering a client. [plaintextSecret] is non-null only for
 * confidential clients and is returned exactly once — it is not recoverable later.
 */
data class ClientRegistration(
    val client: OAuthClient,
    val plaintextSecret: String?,
)
