package io.gateway.authexternal

/** Result of validating a callback's state cookie against the returned state. */
data class VerifiedState(
    val provider: String,
    val codeVerifier: String,
)
