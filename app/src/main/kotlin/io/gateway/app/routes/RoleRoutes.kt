package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.audit.AuditEventType
import io.gateway.audit.AuditLogger
import io.gateway.common.GatewayException
import io.gateway.domain.model.RbacRole
import io.gateway.domain.model.RoleId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.session.SessionService
import kotlinx.datetime.Clock
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private val SLUG_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,62})$")

/**
 * Custom RBAC role management (per tenant). Managing role definitions requires OWNER
 * (or super-admin); assigning existing roles to users requires ADMIN or OWNER.
 * Distinct from the built-in [io.gateway.domain.model.Role] admin gating.
 */
fun Route.roleRoutes(
    roles: RbacRoleRepository,
    users: UserRepository,
    tenants: TenantRepository,
    sessions: SessionService,
    audit: AuditLogger,
    config: GatewayConfig,
) {
    route("/api/admin/roles") {
        get {
            val (tid, _) = call.requireAdmin(tenants, sessions, users, config)
            call.respond(roles.list(tid).map { RoleDtos.RoleResponse.of(it) })
        }

        post {
            val (tid, admin) = call.requireOwner(tenants, sessions, users, config)
            val body = call.receive<RoleDtos.CreateRoleRequest>()
            val slug = body.slug.trim().lowercase()
            if (!SLUG_REGEX.matches(slug)) {
                throw GatewayException.Validation("Slug must be 1-63 chars: lowercase letters, digits, hyphens.")
            }
            if (roles.findBySlug(tid, slug) != null) throw GatewayException.Conflict("Role slug already exists.")
            val role = roles.insert(
                RbacRole(
                    id = RoleId.random(),
                    tenantId = tid,
                    slug = slug,
                    name = body.name.trim().ifEmpty { slug },
                    description = body.description?.trim()?.ifEmpty { null },
                    permissions = normalizePermissions(body.permissions),
                    createdAt = Clock.System.now(),
                ),
            )
            call.recordRole(tid, admin.id, audit, AuditEventType.ROLE_CREATED, "role=${role.slug}")
            call.respond(HttpStatusCode.Created, RoleDtos.RoleResponse.of(role))
        }

        patch("/{id}") {
            val (tid, admin) = call.requireOwner(tenants, sessions, users, config)
            val id = parseRoleId(call.parameters["id"])
            val existing = roles.findById(tid, id) ?: throw GatewayException.NotFound("Role not found.")
            val body = call.receive<RoleDtos.UpdateRoleRequest>()
            val updated = roles.update(
                existing.copy(
                    name = body.name.trim().ifEmpty { existing.slug },
                    description = body.description?.trim()?.ifEmpty { null },
                    permissions = normalizePermissions(body.permissions),
                ),
            )
            call.recordRole(tid, admin.id, audit, AuditEventType.ROLE_UPDATED, "role=${updated.slug}")
            call.respond(RoleDtos.RoleResponse.of(updated))
        }

        delete("/{id}") {
            val (tid, admin) = call.requireOwner(tenants, sessions, users, config)
            val id = parseRoleId(call.parameters["id"])
            val existing = roles.findById(tid, id) ?: throw GatewayException.NotFound("Role not found.")
            roles.delete(tid, id)
            call.recordRole(tid, admin.id, audit, AuditEventType.ROLE_DELETED, "role=${existing.slug}")
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/api/admin/users/{userId}/roles") {
        get {
            val (tid, _) = call.requireAdmin(tenants, sessions, users, config)
            val userId = parseUserId(call.parameters["userId"])
            call.respond(roles.listForUser(tid, userId).map { RoleDtos.RoleResponse.of(it) })
        }

        put {
            val (tid, admin) = call.requireAdmin(tenants, sessions, users, config)
            val userId = parseUserId(call.parameters["userId"])
            users.findById(tid, userId) ?: throw GatewayException.NotFound("User not found.")
            val body = call.receive<RoleDtos.SetUserRolesRequest>()
            val roleIds = body.roleIds.map { parseRoleId(it) }.toSet()
            // Every role must exist in this tenant.
            roleIds.forEach { rid ->
                roles.findById(tid, rid) ?: throw GatewayException.Validation("Unknown role: $rid")
            }
            roles.setUserRoles(tid, userId, roleIds)
            call.recordRole(
                tid, admin.id, audit, AuditEventType.USER_ROLES_CHANGED,
                "user=$userId roles=${roleIds.joinToString(",")}",
            )
            call.respond(roles.listForUser(tid, userId).map { RoleDtos.RoleResponse.of(it) })
        }
    }
}

private suspend fun ApplicationCall.recordRole(
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

private fun parseRoleId(raw: String?): RoleId =
    raw?.let { runCatching { RoleId.parse(it) }.getOrNull() }
        ?: throw GatewayException.NotFound("Role not found.")

private fun parseUserId(raw: String?): UserId =
    raw?.let { runCatching { UserId.parse(it) }.getOrNull() }
        ?: throw GatewayException.NotFound("User not found.")

private fun normalizePermissions(perms: List<String>): Set<String> =
    perms.map { it.trim() }.filter { it.isNotBlank() }.toSet()
