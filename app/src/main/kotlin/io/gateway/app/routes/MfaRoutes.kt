package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.app.ratelimit.AuthRateLimits
import io.gateway.app.ratelimit.RateLimitBackend
import io.gateway.app.ratelimit.RateLimitBucket
import io.gateway.app.tenant.resolveTenant
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.mfa.MfaEnrollmentService
import io.gateway.session.SessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * TOTP MFA enrollment for the logged-in user. Setup returns the secret + otpauth
 * URI; confirm activates MFA and returns one-time recovery codes (shown once).
 */
fun Route.mfaRoutes(
    sessions: SessionService,
    users: UserRepository,
    tenants: TenantRepository,
    mfa: MfaEnrollmentService,
    rateLimitBackend: RateLimitBackend,
    config: GatewayConfig,
) = route("/api/mfa/totp") {
    install(RateLimitBucket) {
        name = AuthRateLimits.GENERAL_NAME
        limit = AuthRateLimits.GENERAL_LIMIT
        window = AuthRateLimits.WINDOW
        backend = rateLimitBackend
    }

    get("/status") {
        val tid = call.resolveTenant(tenants).id
        val user = requireUser(call, tid, sessions, users, config)
        call.respond(MfaDtos.StatusResponse(enabled = mfa.isEnrolled(tid, user.id)))
    }

    post("/setup") {
        val tid = call.resolveTenant(tenants).id
        val user = requireUser(call, tid, sessions, users, config)
        val setup = mfa.beginSetup(tid, user)
        call.respond(MfaDtos.SetupResponse(secret = setup.secret, provisioningUri = setup.provisioningUri))
    }

    post("/confirm") {
        val tid = call.resolveTenant(tenants).id
        val user = requireUser(call, tid, sessions, users, config)
        val body = call.receive<MfaDtos.ConfirmRequest>()
        val recoveryCodes = mfa.confirm(tid, user.id, body.code)
        call.respond(HttpStatusCode.OK, MfaDtos.RecoveryCodesResponse(recoveryCodes))
    }

    delete {
        val tid = call.resolveTenant(tenants).id
        val user = requireUser(call, tid, sessions, users, config)
        mfa.disable(tid, user.id)
        call.respond(HttpStatusCode.OK, AuthDtos.MessageResponse("Two-factor authentication disabled."))
    }
}
