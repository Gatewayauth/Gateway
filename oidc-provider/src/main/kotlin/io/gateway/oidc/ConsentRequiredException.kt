package io.gateway.oidc

/**
 * Thrown by [AuthorizationService] when a client requires consent the user has not
 * yet granted. The app layer turns this into an interaction response so the
 * frontend can render a consent screen, rather than issuing a code.
 */
class ConsentRequiredException(
    val clientId: String,
    val clientName: String,
    val scopes: Set<String>,
) : RuntimeException("Consent required for client $clientId")
