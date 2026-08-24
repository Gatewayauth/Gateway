package io.gateway.common

/**
 * Root of Gateway's typed error hierarchy. Carrying a stable [code] lets the API
 * layer map failures to HTTP responses / OAuth error codes without leaking internals.
 * Subtypes are nested to keep a single top-level declaration per file.
 */
sealed class GatewayException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /** Input failed validation. Maps to HTTP 400 / OAuth `invalid_request`. */
    class Validation(message: String) : GatewayException("validation_error", message)

    /** Authentication failed (bad credentials, invalid token). Maps to HTTP 401. */
    class Unauthenticated(message: String) : GatewayException("unauthenticated", message)

    /** Authenticated but not allowed. Maps to HTTP 403. */
    class Forbidden(message: String) : GatewayException("forbidden", message)

    /** Requested entity does not exist. Maps to HTTP 404. */
    class NotFound(message: String) : GatewayException("not_found", message)

    /** State conflict (duplicate email, reused token). Maps to HTTP 409. */
    class Conflict(message: String) : GatewayException("conflict", message)

    /** Caller exceeded a rate limit. Maps to HTTP 429. */
    class RateLimited(message: String) : GatewayException("rate_limited", message)
}
