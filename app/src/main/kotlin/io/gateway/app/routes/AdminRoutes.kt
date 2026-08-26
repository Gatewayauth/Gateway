package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.app.tenant.resolveTenant
import io.gateway.audit.AuditEventType
import io.gateway.audit.AuditLogger
import io.gateway.audit.AuditQuery
import io.gateway.common.GatewayException
import io.gateway.domain.model.ClientId
import io.gateway.domain.model.Role
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
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

private const val DEFAULT_AUDIT_LIMIT = 100
private const val MAX_AUDIT_LIMIT = 500
private const val DEFAULT_USER_LIMIT = 50
private const val MAX_USER_LIMIT = 200
private const val OWNER_SCAN_LIMIT = 500

/**
 * Per-tenant admin management API. Authorized by the caller's session + role
 * ([Role.ADMIN] or [Role.OWNER]); every action is scoped to the tenant in the
 * `/t/{slug}` path. Role changes require [Role.OWNER] (or a super-admin).
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
    config: GatewayConfig,
) = route("/api/admin") {
    get("/clients") {
        val (tid, _) = call.requireAdmin(tenants, sessions, users, config)
        call.respond(clients.list(tid).map { AdminDtos.ClientSummary.of(it) })
    }

    post("/clients") {
        val (tid, admin) = call.requireAdmin(tenants, sessions, users, config)
        val body = call.receive<AdminDtos.CreateClientRequest>()
        val registration = clientRegistration.register(
            tenantId = tid,
            name = body.name,
            redirectUris = body.redirectUris,
            scopes = body.scopes,
            public = body.public,
            requireConsent = body.requireConsent,
        )
        call.recordAdmin(tid, admin.id, audit, AuditEventType.CLIENT_CREATED, "client_id=${registration.client.id}")
        call.respond(HttpStatusCode.Created, AdminDtos.ClientResponse.of(registration))
    }

    delete("/clients/{id}") {
        val (tid, admin) = call.requireAdmin(tenants, sessions, users, config)
        val id = call.parameters["id"].orEmpty()
        clients.delete(tid, ClientId(id))
        call.recordAdmin(tid, admin.id, audit, AuditEventType.CLIENT_DELETED, "client_id=$id")
        call.respond(HttpStatusCode.NoContent)
    }

    get("/audit") {
        val (tid, _) = call.requireAdmin(tenants, sessions, users, config)
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_AUDIT_LIMIT)
            .coerceIn(1, MAX_AUDIT_LIMIT)
        call.respond(auditQuery.recent(tid, limit).map { AdminDtos.AuditEntry.of(it) })
    }

    get("/users") {
        val (tid, _) = call.requireAdmin(tenants, sessions, users, config)
        val q = call.request.queryParameters
        val limit = (q["limit"]?.toIntOrNull() ?: DEFAULT_USER_LIMIT).coerceIn(1, MAX_USER_LIMIT)
        val offset = q["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        call.respond(users.list(tid, limit, offset).map { AuthDtos.UserResponse.of(it) })
    }

    get("/users/{id}") {
        val (tid, _) = call.requireAdmin(tenants, sessions, users, config)
        val user = users.findById(tid, parseUserId(call.parameters["id"]))
            ?: throw GatewayException.NotFound("User not found.")
        call.respond(AuthDtos.UserResponse.of(user))
    }

    post("/users/{id}/status") {
        val (tid, admin) = call.requireAdmin(tenants, sessions, users, config)
        val userId = parseUserId(call.parameters["id"])
        val body = call.receive<AdminDtos.UserStatusRequest>()
        val status = runCatching { UserStatus.valueOf(body.status) }.getOrNull()
            ?: throw GatewayException.Validation("Invalid status: ${body.status}")
        val user = users.findById(tid, userId) ?: throw GatewayException.NotFound("User not found.")
        val updated = users.update(tid, user.copy(status = status, updatedAt = Clock.System.now()))
        if (status != UserStatus.ACTIVE) sessions.revokeAll(tid, userId)
        call.recordAdmin(tid, admin.id, audit, AuditEventType.USER_STATUS_CHANGED, "user=$userId status=$status")
        call.respond(AuthDtos.UserResponse.of(updated))
    }

    post("/users/{id}/role") {
        val (tid, admin) = call.requireAdmin(tenants, sessions, users, config)
        // Only owners (or super-admins) may change roles.
        if (admin.role != Role.OWNER && !admin.superAdmin) {
            throw GatewayException.Forbidden("Owner role required to manage roles.")
        }
        val userId = parseUserId(call.parameters["id"])
        val body = call.receive<AdminDtos.UserRoleRequest>()
        val role = runCatching { Role.valueOf(body.role) }.getOrNull()
            ?: throw GatewayException.Validation("Invalid role: ${body.role}")
        val user = users.findById(tid, userId) ?: throw GatewayException.NotFound("User not found.")

        // Never leave a tenant without an owner.
        if (user.role == Role.OWNER && role != Role.OWNER && countOwners(users, tid) <= 1) {
            throw GatewayException.Validation("Cannot demote the last owner of the tenant.")
        }

        val updated = users.update(tid, user.copy(role = role, updatedAt = Clock.System.now()))
        val event = if (role == Role.USER) AuditEventType.ADMIN_ROLE_REVOKED else AuditEventType.ADMIN_ROLE_GRANTED
        call.recordAdmin(tid, admin.id, audit, event, "user=$userId role=$role")
        call.respond(AuthDtos.UserResponse.of(updated))
    }

    get("/users/{id}/sessions") {
        val (tid, _) = call.requireAdmin(tenants, sessions, users, config)
        val userId = parseUserId(call.parameters["id"])
        call.respond(sessions.listActive(tid, userId).map { AuthDtos.SessionSummary.of(it, current = false) })
    }

    post("/users/{id}/revoke-sessions") {
        val (tid, admin) = call.requireAdmin(tenants, sessions, users, config)
        val userId = parseUserId(call.parameters["id"])
        sessions.revokeAll(tid, userId)
        call.recordAdmin(tid, admin.id, audit, AuditEventType.SESSIONS_REVOKED, "user=$userId scope=admin")
        call.respond(HttpStatusCode.NoContent)
    }

    post("/keys/rotate") {
        val (tid, admin) = call.requireAdmin(tenants, sessions, users, config)
        signingKeys.rotate(tid)
        call.recordAdmin(tid, admin.id, audit, AuditEventType.SIGNING_KEY_ROTATED, "manual")
        call.respond(HttpStatusCode.OK, AuthDtos.MessageResponse("Signing key rotated."))
    }
}

private suspend fun countOwners(users: UserRepository, tid: TenantId): Int =
    users.list(tid, OWNER_SCAN_LIMIT, 0).count { it.role == Role.OWNER }

/** Records an admin action with the acting admin's real user id as actor. */
private suspend fun ApplicationCall.recordAdmin(
    tenantId: TenantId,
    actor: UserId,
    audit: AuditLogger,
    type: AuditEventType,
    detail: String,
) {
    audit.record(
        tenantId = tenantId,
        type = type,
        actor = actor,
        ip = clientIp(),
        userAgent = userAgent(),
        detail = detail,
    )
}

private fun parseUserId(raw: String?): UserId =
    raw?.let { runCatching { UserId.parse(it) }.getOrNull() }
        ?: throw GatewayException.NotFound("User not found.")

/**
 * Resolves the tenant from the path and the caller from their session, requiring
 * an admin role. Returns the tenant id and the acting admin.
 */
internal suspend fun ApplicationCall.requireAdmin(
    tenants: TenantRepository,
    sessions: SessionService,
    users: UserRepository,
    config: GatewayConfig,
): Pair<TenantId, User> {
    val tenant = resolveTenant(tenants)
    val user = requireUser(this, tenant.id, sessions, users, config)
    if (user.role != Role.ADMIN && user.role != Role.OWNER) {
        throw GatewayException.Forbidden("Admin role required.")
    }
    return tenant.id to user
}

/**
 * As [requireAdmin], but additionally requires OWNER (or a super-admin). Used for
 * privileged management (role definitions, user-role changes).
 */
internal suspend fun ApplicationCall.requireOwner(
    tenants: TenantRepository,
    sessions: SessionService,
    users: UserRepository,
    config: GatewayConfig,
): Pair<TenantId, User> {
    val (tid, user) = requireAdmin(tenants, sessions, users, config)
    if (user.role != Role.OWNER && !user.superAdmin) {
        throw GatewayException.Forbidden("Owner role required.")
    }
    return tid to user
}
