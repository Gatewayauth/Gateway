package io.gateway.app.config

import io.gateway.authexternal.ExternalProviderCredentials
import io.gateway.persistence.DatabaseConfig

/** Fully-resolved application configuration, loaded once at startup. */
data class GatewayConfig(
    val issuer: String,
    val database: DatabaseConfig,
    val sessionTtlHours: Long,
    val sessionCookieName: String,
    val cookieSecure: Boolean,
    /**
     * Email promoted to OWNER + super-admin in the default tenant on startup.
     * Null disables bootstrapping. Idempotent; runs every boot.
     */
    val bootstrapAdminEmail: String?,
    /** Passphrase used to derive the AES/HMAC keys for TOTP secrets and MFA tokens. */
    val encKey: String,
    /** External IdP credentials keyed by provider id (google/github/discord). */
    val externalProviders: Map<String, ExternalProviderCredentials>,
    /** Where the external-login callback redirects the browser after success. */
    val postLoginRedirect: String,
    /** Base URL used to build verification / password-reset links in emails. */
    val mailLinkBaseUrl: String,
    /** Days between automatic signing-key rotations. 0 disables the scheduler. */
    val keyRotationDays: Int,
    /** SMTP delivery settings; null selects the log-only dev mailer. */
    val smtp: SmtpSettings?,
    /** Allowed browser origins for CORS (frontend), e.g. https://app.example.com. */
    val corsOrigins: List<String>,
    /** Redis connection URL (redis://host:port). Null selects in-memory (single-instance) state. */
    val redisUrl: String?,
)
