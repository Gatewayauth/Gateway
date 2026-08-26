package io.gateway.app

import io.gateway.app.config.loadGatewayConfig
import io.gateway.app.di.appModule
import io.gateway.app.config.GatewayConfig
import io.gateway.app.plugins.configureStatusPages
import io.gateway.app.routes.AuthRoutesDeps
import io.gateway.app.routes.accountRoutes
import io.gateway.app.routes.adminRoutes
import io.gateway.app.routes.authRoutes
import io.gateway.app.routes.externalAuthRoutes
import io.gateway.app.routes.externalCallbackRoutes
import io.gateway.app.routes.healthRoutes
import io.gateway.app.routes.mfaRoutes
import io.gateway.app.routes.oauth2Routes
import io.gateway.app.routes.oidcRoutes
import io.gateway.app.routes.provisioningRoutes
import io.gateway.app.routes.roleRoutes
import io.gateway.app.tenant.tenantScoped
import io.gateway.audit.AuditLogger
import io.gateway.audit.AuditQuery
import io.gateway.authexternal.AccountLinkingService
import io.gateway.authexternal.ExternalStateCodec
import io.gateway.authexternal.ProviderRegistry
import io.gateway.authlocal.EmailVerificationService
import io.gateway.authlocal.PasswordAuthenticator
import io.gateway.authlocal.PasswordResetService
import io.gateway.authlocal.RegistrationService
import io.gateway.domain.model.Role
import io.gateway.domain.repository.ExternalIdentityRepository
import io.gateway.domain.repository.OAuthClientRepository
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.gateway.mfa.MfaAttemptLimiter
import io.gateway.mfa.MfaChallengeService
import io.gateway.mfa.MfaEnrollmentService
import io.gateway.oidc.AccessTokenVerifier
import io.gateway.oidc.AuthorizationService
import io.gateway.oidc.ClientAuthenticator
import io.gateway.oidc.ClientRegistrationService
import io.gateway.oidc.ConsentService
import io.gateway.oidc.JwksProvider
import io.gateway.oidc.OidcConfig
import io.gateway.oidc.SigningKeyManager
import io.gateway.oidc.TokenService
import io.gateway.persistence.DatabaseFactory
import io.gateway.session.SessionService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.gateway.app.ratelimit.RateLimitBackend
import io.gateway.app.redis.DistributedLock
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.UUID

/**
 * Application entrypoint (referenced from application.conf). Loads config,
 * connects + migrates the database, installs plugins and DI, then mounts routes.
 */
fun Application.module() {
    val config = loadGatewayConfig()

    // Connect + run Flyway migrations before anything serves traffic.
    DatabaseFactory(config.database).connect()

    install(Koin) {
        slf4jLogger()
        modules(appModule(config))
    }

    configureContentNegotiation()

    // Resolve the real client behind the reverse proxy (nginx forwards it). Must be
    // installed before anything reads `origin.remoteHost` (rate limit, audit). XFF is
    // client-spoofable, so terminate it only at a trusted proxy that overwrites the
    // header — never expose this app directly to untrusted clients with XFF enabled.
    install(XForwardedHeaders)

    configureSecurityHeaders(config)

    install(CallLogging)
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }
    install(CORS) {
        allowCredentials = true
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        // Ktor allows GET/POST/HEAD by default; the API also uses DELETE.
        allowMethod(HttpMethod.Delete)
        config.corsOrigins.forEach { origin ->
            val scheme = origin.substringBefore("://", missingDelimiterValue = "https")
            val host = origin.substringAfter("://")
            allowHost(host, schemes = listOf(scheme))
        }
    }
    configureStatusPages()

    val registration by inject<RegistrationService>()
    val authenticator by inject<PasswordAuthenticator>()
    val emailVerification by inject<EmailVerificationService>()
    val passwordReset by inject<PasswordResetService>()
    val sessions by inject<SessionService>()
    val users by inject<UserRepository>()
    val oidcConfig by inject<OidcConfig>()
    val jwks by inject<JwksProvider>()
    val authorization by inject<AuthorizationService>()
    val tokenService by inject<TokenService>()
    val clientAuth by inject<ClientAuthenticator>()
    val accessTokens by inject<AccessTokenVerifier>()
    val clientRegistration by inject<ClientRegistrationService>()
    val mfaEnrollment by inject<MfaEnrollmentService>()
    val mfaChallenges by inject<MfaChallengeService>()
    val mfaAttempts by inject<MfaAttemptLimiter>()
    val providerRegistry by inject<ProviderRegistry>()
    val stateCodec by inject<ExternalStateCodec>()
    val accountLinking by inject<AccountLinkingService>()
    val externalIdentities by inject<ExternalIdentityRepository>()
    val rbacRoles by inject<RbacRoleRepository>()
    val signingKeys by inject<SigningKeyManager>()
    val audit by inject<AuditLogger>()
    val auditQuery by inject<AuditQuery>()
    val consentService by inject<ConsentService>()
    val clientRepository by inject<OAuthClientRepository>()
    val rateLimitBackend by inject<RateLimitBackend>()
    val distributedLock by inject<DistributedLock>()
    val tenants by inject<TenantRepository>()

    // Load (or generate) each tenant's persistent signing keys before serving traffic.
    runBlocking { tenants.list().forEach { signingKeys.initialize(it.id) } }

    // Optional automatic signing-key rotation (per tenant).
    val clock by inject<Clock>()
    KeyRotationScheduler(signingKeys, tenants, audit, clock, config.keyRotationDays.days, distributedLock).start(this)

    // Promote the configured bootstrap admin in the default tenant (idempotent,
    // runs every boot — lands the first time after that user has registered).
    config.bootstrapAdminEmail?.let { email ->
        runBlocking {
            val root = tenants.findBySlug("default")
            val user = root?.let { users.findByEmail(it.id, email) }
            when {
                root == null -> log.warn("Bootstrap admin skipped: no 'default' tenant.")
                user == null -> log.info("Bootstrap admin '{}' not yet registered; will promote on a later start.", email)
                user.role == Role.OWNER && user.superAdmin -> {} // already promoted
                else -> {
                    users.update(root.id, user.copy(role = Role.OWNER, superAdmin = true, updatedAt = clock.now()))
                    log.info("Bootstrap admin promoted to OWNER + super-admin: {}", email)
                }
            }
        }
    }

    routing {
        // Global (non-tenant) endpoints.
        healthRoutes()
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        provisioningRoutes(tenants, users, sessions, signingKeys, clock, config)
        // Tenant-agnostic external-login callback (one redirect URI per provider,
        // tenant recovered from signed state). `/start` stays tenant-scoped below.
        externalCallbackRoutes(providerRegistry, stateCodec, accountLinking, sessions, tenants, audit, config)

        // Everything else lives under /t/{tenantSlug}; handlers resolve the tenant.
        val authDeps = AuthRoutesDeps(
            registration = registration,
            authenticator = authenticator,
            sessions = sessions,
            users = users,
            identities = externalIdentities,
            tenants = tenants,
            mfa = mfaEnrollment,
            challenges = mfaChallenges,
            mfaAttempts = mfaAttempts,
            emailVerification = emailVerification,
            passwordReset = passwordReset,
            audit = audit,
            rateLimitBackend = rateLimitBackend,
            config = config,
        )
        tenantScoped {
            authRoutes(authDeps)
            accountRoutes(authDeps)
            mfaRoutes(sessions, users, tenants, mfaEnrollment, rateLimitBackend, config)
            externalAuthRoutes(providerRegistry, stateCodec, tenants, config)
            oidcRoutes(oidcConfig, jwks, tenants)
            oauth2Routes(
                sessions, users, tenants, rbacRoles, authorization, tokenService, clientAuth, accessTokens,
                consentService, audit, config,
            )
            adminRoutes(
                clientRegistration = clientRegistration,
                clients = clientRepository,
                users = users,
                tenants = tenants,
                sessions = sessions,
                signingKeys = signingKeys,
                audit = audit,
                auditQuery = auditQuery,
                config = config,
            )
            roleRoutes(rbacRoles, users, tenants, sessions, audit, config)
        }
    }
}

/** JSON content negotiation for the API. */
private fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            },
        )
    }
}

/** Baseline security headers on every response (HSTS only when cookies are Secure / HTTPS). */
private fun Application.configureSecurityHeaders(config: GatewayConfig) {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        if (config.cookieSecure) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }
}
