package io.gateway.oidc

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Static provider configuration. [issuer] is the externally reachable base URL
 * and MUST match what relying parties are configured with (it is the `iss` claim).
 */
data class OidcConfig(
    val issuer: String,
    val accessTokenTtl: Duration = 10.minutes,
    val idTokenTtl: Duration = 10.minutes,
    val refreshTokenTtl: Duration = 30.days,
    val authorizationCodeTtl: Duration = 1.minutes,
)
