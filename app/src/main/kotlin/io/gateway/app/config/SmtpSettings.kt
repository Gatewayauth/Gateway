package io.gateway.app.config

/** SMTP delivery settings. Present only when a host is configured. */
data class SmtpSettings(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val from: String,
    val startTls: Boolean,
)
