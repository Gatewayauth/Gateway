package io.gateway.app.routes

import kotlinx.serialization.Serializable

/** Uniform error body: a stable machine-readable [code] plus a human [message]. */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)
