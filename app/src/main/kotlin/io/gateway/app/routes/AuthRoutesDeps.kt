package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.app.ratelimit.RateLimitBackend
import io.gateway.audit.AuditLogger
import io.gateway.authlocal.EmailVerificationService
import io.gateway.authlocal.PasswordAuthenticator
import io.gateway.authlocal.PasswordResetService
import io.gateway.authlocal.RegistrationService
import io.gateway.domain.repository.ExternalIdentityRepository
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.mfa.MfaAttemptLimiter
import io.gateway.mfa.MfaChallengeService
import io.gateway.mfa.MfaEnrollmentService
import io.gateway.session.SessionService

/** Collaborators for the local-account routes, grouped to keep route signatures small. */
data class AuthRoutesDeps(
    val registration: RegistrationService,
    val authenticator: PasswordAuthenticator,
    val sessions: SessionService,
    val users: UserRepository,
    val identities: ExternalIdentityRepository,
    val tenants: TenantRepository,
    val mfa: MfaEnrollmentService,
    val challenges: MfaChallengeService,
    val mfaAttempts: MfaAttemptLimiter,
    val emailVerification: EmailVerificationService,
    val passwordReset: PasswordResetService,
    val audit: AuditLogger,
    val rateLimitBackend: RateLimitBackend,
    val config: GatewayConfig,
)
