package io.gateway.app.di

import io.gateway.app.LogMailer
import io.gateway.app.SmtpMailer
import io.gateway.app.SystemClock
import io.gateway.app.config.GatewayConfig
import io.gateway.app.ratelimit.InMemoryRateLimitBackend
import io.gateway.app.ratelimit.RateLimitBackend
import io.gateway.app.ratelimit.RedisRateLimitBackend
import io.gateway.app.redis.DistributedLock
import io.gateway.app.redis.NoopDistributedLock
import io.gateway.app.redis.RedisDistributedLock
import io.gateway.app.redis.RedisMfaAttemptLimiter
import io.gateway.mfa.InMemoryMfaAttemptLimiter
import io.gateway.authexternal.AccountLinkingService
import io.gateway.authexternal.ExternalProviders
import io.gateway.authexternal.ExternalStateCodec
import io.gateway.audit.AuditLogger
import io.gateway.audit.AuditQuery
import io.gateway.authlocal.Argon2PasswordHasher
import io.gateway.authlocal.EmailVerificationService
import io.gateway.authlocal.PasswordAuthenticator
import io.gateway.authlocal.PasswordResetService
import io.gateway.authlocal.RegistrationService
import io.gateway.common.Hmac
import io.gateway.common.SecretCipher
import io.gateway.common.Sha256
import io.gateway.domain.auth.PasswordHasher
import io.gateway.domain.notification.Mailer
import io.gateway.domain.repository.AccountTokenRepository
import io.gateway.domain.repository.AuthorizationCodeRepository
import io.gateway.domain.repository.ConsentRepository
import io.gateway.domain.repository.CredentialRepository
import io.gateway.domain.repository.OAuthClientRepository
import io.gateway.domain.repository.ExternalIdentityRepository
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.domain.repository.RecoveryCodeRepository
import io.gateway.domain.repository.RefreshTokenRepository
import io.gateway.domain.repository.SessionRepository
import io.gateway.domain.repository.SigningKeyRepository
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.TotpRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.gateway.mfa.MfaAttemptLimiter
import io.gateway.mfa.MfaChallengeService
import io.gateway.mfa.MfaEnrollmentService
import io.gateway.mfa.TotpService
import io.gateway.oidc.AccessTokenVerifier
import io.gateway.oidc.AuthorizationService
import io.gateway.oidc.ClientAuthenticator
import io.gateway.oidc.ClientRegistrationService
import io.gateway.oidc.ConsentService
import io.gateway.oidc.JwksProvider
import io.gateway.oidc.JwtIssuer
import io.gateway.oidc.OidcConfig
import io.gateway.oidc.SigningKeyManager
import io.gateway.oidc.TokenService
import io.gateway.persistence.repository.ExposedAccountTokenRepository
import io.gateway.persistence.repository.ExposedAuthorizationCodeRepository
import io.gateway.persistence.repository.ExposedAuditLogger
import io.gateway.persistence.repository.ExposedConsentRepository
import io.gateway.persistence.repository.ExposedCredentialRepository
import io.gateway.persistence.repository.ExposedExternalIdentityRepository
import io.gateway.persistence.repository.ExposedRbacRoleRepository
import io.gateway.persistence.repository.ExposedOAuthClientRepository
import io.gateway.persistence.repository.ExposedRecoveryCodeRepository
import io.gateway.persistence.repository.ExposedRefreshTokenRepository
import io.gateway.persistence.repository.ExposedSessionRepository
import io.gateway.persistence.repository.ExposedSigningKeyRepository
import io.gateway.persistence.repository.ExposedTenantRepository
import io.gateway.persistence.repository.ExposedTotpRepository
import io.gateway.persistence.repository.ExposedUserRepository
import io.gateway.session.SessionConfig
import io.gateway.session.SessionService
import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import kotlin.time.Duration.Companion.hours

/** Wires all singletons. Repositories bind domain ports to their Exposed implementations. */
fun appModule(config: GatewayConfig) = module {
    single { config }

    single<Clock> { SystemClock() }
    single<PasswordHasher> { Argon2PasswordHasher() }

    single<UserRepository> { ExposedUserRepository() }
    single<CredentialRepository> { ExposedCredentialRepository() }
    single<SessionRepository> { ExposedSessionRepository() }
    single<OAuthClientRepository> { ExposedOAuthClientRepository() }
    single<AuthorizationCodeRepository> { ExposedAuthorizationCodeRepository() }
    single<RefreshTokenRepository> { ExposedRefreshTokenRepository() }
    single<TotpRepository> { ExposedTotpRepository() }
    single<RecoveryCodeRepository> { ExposedRecoveryCodeRepository() }
    single<ExternalIdentityRepository> { ExposedExternalIdentityRepository() }
    single<SigningKeyRepository> { ExposedSigningKeyRepository() }
    single<ConsentRepository> { ExposedConsentRepository(get()) }
    single<AccountTokenRepository> { ExposedAccountTokenRepository() }
    single<RbacRoleRepository> { ExposedRbacRoleRepository() }
    single<TenantRepository> { ExposedTenantRepository() }
    single<Mailer> { config.smtp?.let { SmtpMailer(it) } ?: LogMailer() }
    single<AuditLogger> { ExposedAuditLogger(get()) }
    single<AuditQuery> { get<AuditLogger>() as AuditQuery }

    // Derive independent AES + HMAC keys from the configured passphrase.
    single { SecretCipher(Sha256.hash(config.encKey)) }
    single { Hmac(Sha256.hash("mfa-challenge:" + config.encKey)) }
    single { TotpService(issuer = config.issuer) }
    single { MfaEnrollmentService(get(), get(), get(), get(), get(), get()) }
    single { MfaChallengeService(get(), get()) }

    // Multi-instance state (rate limit, MFA lockout, key-rotation lock) uses Redis when
    // configured; otherwise in-memory/no-op impls keep single-instance + tests dependency-free.
    val redisUrl = config.redisUrl
    if (redisUrl != null) {
        single { RedisClient.create(redisUrl) }
        single<RedisCommands<String, String>> { get<RedisClient>().connect().sync() }
        single<RateLimitBackend> { RedisRateLimitBackend(get()) }
        single<MfaAttemptLimiter> { RedisMfaAttemptLimiter(get()) }
        single<DistributedLock> { RedisDistributedLock(get()) }
    } else {
        single<RateLimitBackend> { InMemoryRateLimitBackend(get()) }
        single<MfaAttemptLimiter> { InMemoryMfaAttemptLimiter(get()) }
        single<DistributedLock> { NoopDistributedLock() }
    }

    // External identity providers (Google/GitHub/Discord).
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
    single { ExternalProviders.build(config.externalProviders, get()) }
    single { AccountLinkingService(get(), get(), get()) }
    single { ExternalStateCodec(Sha256.hash("external-state:" + config.encKey), get()) }

    single { RegistrationService(get(), get(), get(), get(), get(), config.mailLinkBaseUrl) }
    single { PasswordAuthenticator(get(), get(), get()) }
    single { EmailVerificationService(get(), get(), get(), get(), config.mailLinkBaseUrl) }
    single { PasswordResetService(get(), get(), get(), get(), get(), get(), get(), config.mailLinkBaseUrl) }

    single { SessionConfig(ttl = config.sessionTtlHours.hours, cookieName = config.sessionCookieName) }
    single { SessionService(get(), get(), get()) }

    single { OidcConfig(issuer = config.issuer) }
    single { SigningKeyManager(get(), get(), get()) }
    single { JwtIssuer(get(), get(), get()) }
    single { JwksProvider(get()) }
    single { AccessTokenVerifier(get(), get()) }
    single { AuthorizationService(get(), get(), get(), get(), get()) }
    single { TokenService(get(), get(), get(), get(), get(), get(), get()) }
    single { ClientAuthenticator(get()) }
    single { ClientRegistrationService(get(), get()) }
    single { ConsentService(get(), get()) }
}
