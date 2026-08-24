package io.gateway.app.config

import io.gateway.authexternal.ExternalProviderCredentials
import io.gateway.persistence.DatabaseConfig
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig

/** Reads the `gateway { ... }` block from application.conf / env into [GatewayConfig]. */
fun Application.loadGatewayConfig(): GatewayConfig {
    val root = environment.config.config("gateway")
    val db = root.config("db")
    val session = root.config("session")
    val corsOrigins = (root.propertyOrNull("cors.origins")?.getString() ?: "http://localhost:3000")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return GatewayConfig(
        issuer = root.property("issuer").getString().trimEnd('/'),
        database = DatabaseConfig(
            jdbcUrl = db.property("url").getString(),
            username = db.property("user").getString(),
            password = db.property("password").getString(),
            driverClassName = db.property("driver").getString(),
            maxPoolSize = db.optInt("maxPoolSize", default = 10),
        ),
        sessionTtlHours = session.optInt("ttlHours", default = 12).toLong(),
        sessionCookieName = session.property("cookieName").getString(),
        cookieSecure = session.property("cookieSecure").getString().toBoolean(),
        adminToken = root.propertyOrNull("admin.token")?.getString()?.takeIf { it.isNotBlank() },
        encKey = root.property("security.encKey").getString(),
        externalProviders = loadExternalProviders(root),
        // Land the browser on the frontend after external login, not the backend.
        // Defaults to the first CORS origin (the UI); falls back to issuer only if
        // no CORS origins are configured.
        postLoginRedirect = root.propertyOrNull("external.postLoginRedirect")?.getString()?.takeIf { it.isNotBlank() }
            ?: corsOrigins.firstOrNull()
            ?: root.property("issuer").getString().trimEnd('/'),
        mailLinkBaseUrl = root.propertyOrNull("mail.linkBaseUrl")?.getString()?.takeIf { it.isNotBlank() }
            ?: root.property("issuer").getString().trimEnd('/'),
        keyRotationDays = root.propertyOrNull("keys.rotationDays")?.getString()?.toIntOrNull() ?: 0,
        smtp = loadSmtp(root),
        corsOrigins = corsOrigins,
        redisUrl = root.propertyOrNull("redis.url")?.getString()?.takeIf { it.isNotBlank() },
    )
}

private const val DEFAULT_SMTP_PORT = 587

private fun loadSmtp(root: ApplicationConfig): SmtpSettings? {
    val smtp = root.configOrNull("mail.smtp") ?: return null
    val host = smtp.propertyOrNull("host")?.getString()?.takeIf { it.isNotBlank() } ?: return null
    return SmtpSettings(
        host = host,
        port = smtp.propertyOrNull("port")?.getString()?.toIntOrNull() ?: DEFAULT_SMTP_PORT,
        username = smtp.propertyOrNull("username")?.getString()?.takeIf { it.isNotBlank() },
        password = smtp.propertyOrNull("password")?.getString()?.takeIf { it.isNotBlank() },
        from = smtp.propertyOrNull("from")?.getString()?.takeIf { it.isNotBlank() } ?: "no-reply@gateway.local",
        startTls = smtp.propertyOrNull("startTls")?.getString()?.toBoolean() ?: true,
    )
}

private fun loadExternalProviders(root: ApplicationConfig): Map<String, ExternalProviderCredentials> {
    val external = root.configOrNull("external") ?: return emptyMap()
    return listOf("google", "github", "discord").mapNotNull { id ->
        val block = external.configOrNull(id) ?: return@mapNotNull null
        val clientId = block.propertyOrNull("clientId")?.getString().orEmpty()
        val clientSecret = block.propertyOrNull("clientSecret")?.getString().orEmpty()
        if (clientId.isBlank() || clientSecret.isBlank()) {
            null
        } else {
            id to ExternalProviderCredentials(clientId, clientSecret)
        }
    }.toMap()
}

private fun ApplicationConfig.configOrNull(path: String): ApplicationConfig? =
    runCatching { config(path) }.getOrNull()

private fun ApplicationConfig.optInt(path: String, default: Int): Int =
    propertyOrNull(path)?.getString()?.toIntOrNull() ?: default
