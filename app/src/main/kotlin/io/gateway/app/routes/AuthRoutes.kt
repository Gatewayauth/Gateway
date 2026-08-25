package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.app.ratelimit.AuthRateLimits
import io.gateway.app.ratelimit.RateLimitBackend
import io.gateway.app.ratelimit.RateLimitBucket
import io.gateway.app.ratelimit.enforceRateLimit
import io.gateway.app.tenant.resolveTenant
import io.gateway.audit.AuditEventType
import io.gateway.common.GatewayException
import io.gateway.domain.model.SessionId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.repository.UserRepository
import io.gateway.session.SessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/**
 * Local-account authentication API (register / verify / login / MFA / password reset).
 * Mounted under `/t/{tenantSlug}`; every handler resolves the tenant first and passes
 * its id to services so all data access is tenant-scoped.
 */
fun Route.authRoutes(deps: AuthRoutesDeps) = with(deps) {
    route("/api/auth") {
        installGeneralRateLimit(rateLimitBackend)

        suspend fun ApplicationCall.enforceSensitiveLimit() = enforceRateLimit(
            rateLimitBackend,
            AuthRateLimits.SENSITIVE_NAME,
            AuthRateLimits.SENSITIVE_LIMIT,
            AuthRateLimits.WINDOW,
        )

        post("/register") {
            call.enforceSensitiveLimit()
            val tid = call.resolveTenant(tenants).id
            val body = call.receive<AuthDtos.RegisterRequest>()
            val outcome = registration.register(tid, body.email, body.displayName, body.password.toCharArray())
            // Side effects only for a real new account; the response is identical either
            // way so signup can't be used to probe which emails are registered.
            if (outcome.created) {
                emailVerification.startVerification(tid, outcome.user)
                audit.record(tid, AuditEventType.ACCOUNT_REGISTERED, outcome.user.id, call.clientIp(), call.userAgent())
            }
            call.respond(HttpStatusCode.Created, AuthDtos.UserResponse.of(outcome.user))
        }

        post("/verify") {
            val tid = call.resolveTenant(tenants).id
            val body = call.receive<AuthDtos.VerifyEmailRequest>()
            call.respond(AuthDtos.UserResponse.of(emailVerification.verify(tid, body.token)))
        }

        post("/verify/send") {
            val tid = call.resolveTenant(tenants).id
            val user = requireUser(call, tid, sessions, users, config)
            emailVerification.startVerification(tid, user)
            call.respond(AuthDtos.MessageResponse("Verification email sent."))
        }

        post("/password/forgot") {
            call.enforceSensitiveLimit()
            val tid = call.resolveTenant(tenants).id
            val body = call.receive<AuthDtos.ForgotPasswordRequest>()
            passwordReset.request(tid, body.email)
            // Always 200 — never reveal whether the email exists.
            call.respond(AuthDtos.MessageResponse("If the account exists, a reset email was sent."))
        }

        post("/password/reset") {
            call.enforceSensitiveLimit()
            val tid = call.resolveTenant(tenants).id
            val body = call.receive<AuthDtos.ResetPasswordRequest>()
            passwordReset.reset(tid, body.token, body.password.toCharArray())
            call.respond(AuthDtos.MessageResponse("Password updated."))
        }

        post("/login") {
            call.enforceSensitiveLimit()
            val tid = call.resolveTenant(tenants).id
            val body = call.receive<AuthDtos.LoginRequest>()
            val user = try {
                authenticator.authenticate(tid, body.email, body.password.toCharArray())
            } catch (e: GatewayException) {
                audit.record(
                    tid,
                    AuditEventType.LOGIN_FAILED,
                    actor = null,
                    ip = call.clientIp(),
                    userAgent = call.userAgent(),
                    detail = "email=${body.email}",
                )
                throw e
            }
            if (mfa.isEnrolled(tid, user.id)) {
                call.respond(AuthDtos.MfaChallengeResponse(mfaToken = challenges.issue(user.id)))
            } else {
                audit.record(tid, AuditEventType.LOGIN_SUCCEEDED, user.id, call.clientIp(), call.userAgent(), "amr=pwd")
                establishSession(call, tid, sessions, config, user, setOf("pwd"))
            }
        }

        post("/login/mfa") {
            call.enforceSensitiveLimit()
            val tid = call.resolveTenant(tenants).id
            val body = call.receive<AuthDtos.MfaLoginRequest>()
            val userId = challenges.verify(body.mfaToken)
                ?: throw GatewayException.Unauthenticated("Invalid or expired MFA challenge.")
            mfaAttempts.assertNotLocked(userId)
            if (!mfa.verifySecondFactor(tid, userId, body.code)) {
                mfaAttempts.recordFailure(userId)
                audit.record(tid, AuditEventType.MFA_CHALLENGE_FAILED, userId, call.clientIp(), call.userAgent())
                throw GatewayException.Unauthenticated("Invalid MFA code.")
            }
            mfaAttempts.reset(userId)
            val user = users.findById(tid, userId) ?: throw GatewayException.Unauthenticated("Unknown user.")
            audit.record(tid, AuditEventType.LOGIN_SUCCEEDED, user.id, call.clientIp(), call.userAgent(), "amr=pwd,otp")
            establishSession(call, tid, sessions, config, user, setOf("pwd", "otp"))
        }
    }
}

/** Session-management endpoints for the logged-in user. Shares the /api/auth node bucket. */
fun Route.accountRoutes(deps: AuthRoutesDeps) = with(deps) {
    route("/api/auth") {
        post("/logout") {
            val tid = call.resolveTenant(tenants).id
            SessionCookies.read(call, config)?.let { token ->
                sessions.resolve(tid, token)?.let { sessions.revoke(tid, it.id) }
            }
            SessionCookies.clear(call, config)
            call.respond(HttpStatusCode.OK, AuthDtos.MessageResponse("Logged out."))
        }

        get("/me") {
            val tid = call.resolveTenant(tenants).id
            call.respond(AuthDtos.UserResponse.of(requireUser(call, tid, sessions, users, config)))
        }

        // Providers this account has actually linked (e.g. signed up via Discord).
        // The account UI marks these as connected.
        get("/identities") {
            val tid = call.resolveTenant(tenants).id
            val user = requireUser(call, tid, sessions, users, config)
            call.respond(identities.listForUser(tid, user.id).map { it.provider }.distinct())
        }

        get("/sessions") {
            val tid = call.resolveTenant(tenants).id
            val user = requireUser(call, tid, sessions, users, config)
            val currentId = SessionCookies.read(call, config)?.let { sessions.resolve(tid, it)?.id?.toString() }
            val summaries = sessions.listActive(tid, user.id)
                .map { AuthDtos.SessionSummary.of(it, current = it.id.toString() == currentId) }
            call.respond(summaries)
        }

        delete("/sessions/{id}") {
            val tid = call.resolveTenant(tenants).id
            val user = requireUser(call, tid, sessions, users, config)
            val sessionId = call.parameters["id"]?.let { runCatching { SessionId(UUID.fromString(it)) }.getOrNull() }
                ?: throw GatewayException.NotFound("Session not found.")
            val target = sessions.findById(tid, sessionId)
            if (target == null || target.userId != user.id) throw GatewayException.NotFound("Session not found.")
            sessions.revoke(tid, target.id)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/logout-all") {
            val tid = call.resolveTenant(tenants).id
            val user = requireUser(call, tid, sessions, users, config)
            sessions.revokeAll(tid, user.id)
            audit.record(tid, AuditEventType.SESSIONS_REVOKED, user.id, call.clientIp(), call.userAgent(), "scope=self")
            SessionCookies.clear(call, config)
            call.respond(AuthDtos.MessageResponse("All sessions revoked."))
        }
    }
}

/** Installs the general per-IP rate-limit bucket over the enclosing route group. */
private fun Route.installGeneralRateLimit(backend: RateLimitBackend) {
    install(RateLimitBucket) {
        name = AuthRateLimits.GENERAL_NAME
        limit = AuthRateLimits.GENERAL_LIMIT
        window = AuthRateLimits.WINDOW
        this.backend = backend
    }
}

/** Creates a session, sets the cookie, and responds with the user. */
internal suspend fun establishSession(
    call: ApplicationCall,
    tenantId: TenantId,
    sessions: SessionService,
    config: GatewayConfig,
    user: User,
    amr: Set<String>,
) {
    val issued = sessions.create(
        tenantId = tenantId,
        userId = user.id,
        amr = amr,
        ip = call.request.local.remoteHost,
        userAgent = call.request.headers["User-Agent"],
    )
    SessionCookies.set(call, config, issued.rawToken, config.sessionTtlHours.hours.inWholeSeconds)
    call.respond(AuthDtos.UserResponse.of(user))
}

/** Resolves the current authenticated user from the session cookie or throws 401. */
internal suspend fun requireUser(
    call: ApplicationCall,
    tenantId: TenantId,
    sessions: SessionService,
    users: UserRepository,
    config: GatewayConfig,
): User {
    val token = SessionCookies.read(call, config)
        ?: throw GatewayException.Unauthenticated("No session.")
    val session = sessions.resolve(tenantId, token)
        ?: throw GatewayException.Unauthenticated("Session expired.")
    return users.findById(tenantId, session.userId)
        ?: throw GatewayException.Unauthenticated("Unknown user.")
}
