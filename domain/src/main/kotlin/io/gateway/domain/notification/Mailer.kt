package io.gateway.domain.notification

/**
 * Outbound email SPI. The default implementation logs (dev); production swaps in
 * an SMTP/API-backed mailer without touching callers.
 */
interface Mailer {
    suspend fun send(to: String, subject: String, body: String)
}
