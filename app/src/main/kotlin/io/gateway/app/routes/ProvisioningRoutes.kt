package io.gateway.app.routes

import io.gateway.common.ConstantTime
import io.gateway.common.GatewayException
import io.gateway.domain.model.Tenant
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.TenantStatus
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.time.Clock
import io.gateway.oidc.SigningKeyManager
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

/**
 * Global super-admin API for creating/listing tenants. Guarded by the bootstrap
 * admin token (the same `X-Admin-Token`); mounted outside the `/t/{slug}` tree.
 */
fun Route.provisioningRoutes(
    tenants: TenantRepository,
    signingKeys: SigningKeyManager,
    clock: Clock,
    adminToken: String?,
) = route("/api/provisioning/tenants") {
    get {
        call.requireSuperAdmin(adminToken)
        call.respond(tenants.list().map { ProvisioningDtos.TenantResponse.of(it) })
    }

    post {
        call.requireSuperAdmin(adminToken)
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

private fun ApplicationCall.requireSuperAdmin(adminToken: String?) {
    if (adminToken == null) throw GatewayException.Forbidden("Provisioning API is disabled.")
    val provided = request.headers["X-Admin-Token"]
        ?: throw GatewayException.Unauthenticated("Missing admin token.")
    if (!ConstantTime.equals(provided, adminToken)) {
        throw GatewayException.Unauthenticated("Invalid admin token.")
    }
}
