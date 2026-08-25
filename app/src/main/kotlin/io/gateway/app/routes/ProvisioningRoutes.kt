package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.common.GatewayException
import io.gateway.domain.model.Tenant
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.TenantStatus
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import io.gateway.oidc.SigningKeyManager
import io.gateway.session.SessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

private val SLUG_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,62})$")

/** Payloads for the global super-admin tenant provisioning API. */
object ProvisioningDtos {
    @Serializable
    data class CreateTenantRequest(val slug: String, val name: String)

    @Serializable
    data class TenantResponse(val id: String, val slug: String, val name: String, val status: String) {
        companion object {
            fun of(t: Tenant) = TenantResponse(t.id.toString(), t.slug, t.name, t.status.name)
        }
    }
}

/** Super-admins live in the default tenant; their session cookie resolves there. */
private const val DEFAULT_TENANT_SLUG = "default"

/**
 * Global super-admin API for creating/listing tenants. Authorized by the caller's
 * session + [User.superAdmin]; mounted outside the `/t/{slug}` tree. Super-admins
 * authenticate against the default tenant.
 */
fun Route.provisioningRoutes(
    tenants: TenantRepository,
    users: UserRepository,
    sessions: SessionService,
    signingKeys: SigningKeyManager,
    clock: Clock,
    config: GatewayConfig,
) = route("/api/provisioning/tenants") {
    get {
        call.requireSuperAdmin(tenants, sessions, users, config)
        call.respond(tenants.list().map { ProvisioningDtos.TenantResponse.of(it) })
    }

    post {
        call.requireSuperAdmin(tenants, sessions, users, config)
        val body = call.receive<ProvisioningDtos.CreateTenantRequest>()
        val slug = body.slug.trim().lowercase()
        if (!SLUG_REGEX.matches(slug)) {
            throw GatewayException.Validation("Slug must be 1-63 chars: lowercase letters, digits, hyphens.")
        }
        if (tenants.findBySlug(slug) != null) throw GatewayException.Conflict("Tenant slug already exists.")
        val tenant = tenants.insert(
            Tenant(
                id = TenantId.random(),
                slug = slug,
                name = body.name.trim().ifEmpty { slug },
                status = TenantStatus.ACTIVE,
                createdAt = clock.now(),
            ),
        )
        // Generate the new tenant's signing keys so its OIDC endpoints work immediately.
        signingKeys.initialize(tenant.id)
        call.respond(HttpStatusCode.Created, ProvisioningDtos.TenantResponse.of(tenant))
    }
}

private suspend fun ApplicationCall.requireSuperAdmin(
    tenants: TenantRepository,
    sessions: SessionService,
    users: UserRepository,
    config: GatewayConfig,
) {
    val root = tenants.findBySlug(DEFAULT_TENANT_SLUG)
        ?: throw GatewayException.Forbidden("Provisioning API is unavailable.")
    val user = requireUser(this, root.id, sessions, users, config)
    if (!user.superAdmin) throw GatewayException.Forbidden("Super-admin required.")
}
