package io.gateway.mfa

/** Result of starting TOTP enrollment: the shared [secret] and its [provisioningUri]. */
data class TotpSetup(
    val secret: String,
    val provisioningUri: String,
)
