package io.gateway.app.routes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** MFA enrollment payloads. Nested to keep one top-level type per file. */
object MfaDtos {

    @Serializable
    data class SetupResponse(
        val secret: String,
        @SerialName("provisioning_uri") val provisioningUri: String,
    )

    @Serializable
    data class ConfirmRequest(val code: String)

    @Serializable
    data class StatusResponse(val enabled: Boolean)

    @Serializable
    data class RecoveryCodesResponse(
        @SerialName("recovery_codes") val recoveryCodes: List<String>,
    )
}
