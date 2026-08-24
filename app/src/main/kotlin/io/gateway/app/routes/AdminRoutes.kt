package io.gateway.app.routes

import io.gateway.app.tenant.resolveTenant
import io.gateway.audit.AuditEventType
import io.gateway.audit.AuditLogger
import io.gateway.audit.AuditQuery
import io.gateway.common.ConstantTime
import io.gateway.common.GatewayException
import io.gateway.domain.model.ClientId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.repository.OAuthClientRepository
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.oidc.ClientRegistrationService
import io.gateway.oidc.SigningKeyManager
import io.gateway.session.SessionService
import kotlinx.datetime.Clock
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

// Audit actor for token-authed admin actions (no per-admin identity yet).
private const val ADMIN_ACTOR = "bootstrap-admin"
private const val DEFAULT_AUDIT_LIMIT = 100
private const val MAX_AUDIT_LIMIT = 500
private const val DEFAULT_USER_LIMIT = 50
private const val MAX_USER_LIMIT = 200

/**
 * Per-tenant admin management API. Guarded by a bootstrap token (`X-Admin-Token`);
 * every action is scoped to the tenant in the `/t/{slug}` path. Role-based admin
 * accounts are a follow-up. Disabled entirely when no token is configured.
 */
fun Route.adminRoutes(
    clientRegistration: ClientRegistrationService,
    clients: OAuthClientRepository,
    users: UserRepository,
    tenants: TenantRepository,
    sessions: SessionService,
    signingKeys: SigningKeyManager,
    audit: AuditLogger,
    auditQuery: AuditQuery,
    adminToken: String?,
) = route("/api/admin") {
    get("/clients") {
        val tid = call.requireAdmin(tenants, adminToken)
        call.respond(clients.list(tid).map { AdminDtos.ClientSummary.of(it) })
    }

    post("/clients") {
        val tid = call.requireAdmin(tenants, adminToken)
        val body = call.receive<AdminDtos.CreateClientRequest>()
        val registration = clientRegistration.register(
            tenantId = tid,
            name = body.name,
            redirectUris = body.redirectUris,
            scopes = body.scopes,
            public = body.public,
            requireConsent = body.requireConsent,
        )
        call.recordAdmin(tid, audit, AuditEventType.CLIENT_CREATED, "client_id=${registration.client.id}")
        call.respond(HttpStatusCode.Created, AdminDtos.ClientResponse.of(registration))
    }

    delete("/clients/{id}") {
        val tid = call.requireAdmin(tenants, adminToken)
        val id = call.parameters["id"].orEmpty()
        clients.delete(tid, ClientId(id))
        call.recordAdmin(tid, audit, AuditEventType.CLIENT_DELETED, "client_id=$id")
        call.respond(HttpStatusCode.NoContent)
    }

    get("/audit") {
        val tid = call.requireAdmin(tenants, adminToken)
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_AUDIT_LIMIT)
            .coerceIn(1, MAX_AUDIT_LIMIT)
        call.respond(auditQuery.recent(tid, limit).map { AdminDtos.AuditEntry.of(it) })
    }

    get("/users") {
        val tid = call.requireAdmin(tenants, adminToken)
        val q = call.request.queryParameters
        val limit = (q["limit"]?.toIntOrNull() ?: DEFAULT_USER_LIMIT).coerceIn(1, MAX_USER_LIMIT)
        val offset = q["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        call.respond(users.list(tid, limit, offset).map { AuthDtos.UserResponse.of(it) })
    }

    get("/users/{id}") {
        val tid = call.requireAdmin(tenants, adminToken)
        val user = users.findById(tid, parseUserId(call.parameters["id"]))
            ?: throw GatewayException.NotFound("User not found.")
        call.respond(AuthDtos.UserResponse.of(user))
    }

    post("/users/{id}/status") {
        val tid = call.requireAdmin(tenants, adminToken)
        val userId = parseUserId(call.parameters["id"])
        val body = call.receive<AdminDtos.UserStatusRequest>()
        val status = runCatching { UserStatus.valueOf(body.status) }.getOrNull()
            ?: throw GatewayException.Validation("Invalid status: ${body.status}")
        val user = users.findById(tid, userId) ?: throw GatewayException.NotFound("User not found.")
        val updated = users.update(tid, user.copy(status = status, updatedAt = Clock.System.now()))
        if (status != UserStatus.ACTIVE) sessions.revokeAll(tid, userId)
        call.recordAdmin(tid, audit, AuditEventType.USER_STATUS_CHANGED, "user=$userId status=$status")
        call.respond(AuthDtos.UserResponse.of(updated))
    }

    get("/users/{id}/sessions") {
        val tid = call.requireAdmin(tenants, adminToken)
        val userId = parseUserId(call.parameters["id"])
        call.respond(sessions.listActive(tid, userId).map { AuthDtos.SessionSummary.of(it, current = false) })
    }

    post("/users/{id}/revoke-sessions") {
        val tid = call.requireAdmin(tenants, adminToken)
        val userId = parseUserId(call.parameters["id"])
        sessions.revokeAll(tid, userId)
        call.recordAdmin(tid, audit, AuditEventType.SESSIONS_REVOKED, "user=$userId scope=admin")
        call.respond(HttpStatusCode.NoContent)
    }

    post("/keys/rotate") {
        val tid = call.requireAdmin(tenants, adminToken)
        signingKeys.rotate(tid)
        call.recordAdmin(tid, audit, AuditEventType.SIGNING_KEY_ROTATED, "manual")
        call.respond(HttpStatusCode.OK, AuthDtos.MessageResponse("Signing key rotated."))
    }
}

/** Records an admin action, stamping the bootstrap-admin actor label (no per-admin id yet). */
private suspend fun ApplicationCall.recordAdmin(
    tenantId: TenantId,
    audit: AuditLogger,
    type: AuditEventType,
    detail: String,
) {
    audit.record(
        tenantId = tenantId,
        type = type,
        actor = null,
        ip = clientIp(),
        userAgent = userAgent(),
        detail = detail,
        actorLabel = ADMIN_ACTOR,
    )
}

private fun parseUserId(raw: String?): UserId =
    raw?.let { runCatching { UserId.parse(it) }.getOrNull() }
        ?: throw GatewayException.NotFound("User not found.")

/** Verifies the admin token, then resolves and returns the tenant for the request path. */
private suspend fun ApplicationCall.requireAdmin(tenants: TenantRepository, adminToken: String?): TenantId {
    if (adminToken == null) throw GatewayException.Forbidden("Admin API is disabled.")
    val provided = request.headers["X-Admin-Token"]
        ?: throw GatewayException.Unauthenticated("Missing admin token.")
    if (!ConstantTime.equals(provided, adminToken)) {
        throw GatewayException.Unauthenticated("Invalid admin token.")
    }
    return resolveTenant(tenants).id
}
