package io.gateway.mfa

/** A generated recovery code: [plaintext] is shown once, [hash] is what gets stored. */
data class RecoveryCode(
    val plaintext: String,
    val hash: String,
)
