package io.gateway.oidc

/**
 * An OAuth2/OIDC protocol error (RFC 6749 §5.2). [error] is the spec error code
 * returned in the JSON body; [unauthorized] maps to HTTP 401 (client auth
 * failures), otherwise 400.
 */
class OAuthException(
    val error: String,
    val description: String,
    val unauthorized: Boolean = false,
) : RuntimeException(description) {
    companion object {
        fun invalidRequest(description: String) = OAuthException("invalid_request", description)
        fun invalidGrant(description: String) = OAuthException("invalid_grant", description)
        fun invalidClient(description: String) = OAuthException("invalid_client", description, unauthorized = true)
        fun invalidScope(description: String) = OAuthException("invalid_scope", description)
        fun unsupportedGrantType(description: String) = OAuthException("unsupported_grant_type", description)

        /**
         * The authenticated user is not permitted to access this client. Thrown at
         * the authorize step (non-redirect) so the user sees the Gateway's own
         * "no access" page rather than being bounced back to the relying party,
         * which would just surface its own error.
         */
        fun accessDenied(description: String) = OAuthException("access_denied", description)
    }
}
