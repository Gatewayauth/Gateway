package io.gateway.app.tenant

import io.gateway.common.GatewayException
import io.gateway.domain.model.Tenant
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.TenantRepository
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.util.AttributeKey

/** Holds the tenant resolved from the `/t/{tenantSlug}` path segment for the current call. */
val TenantAttributeKey: AttributeKey<Tenant> = AttributeKey("gateway.tenant")

/**
 * Resolves `{tenantSlug}` to a [Tenant] (cached on the call), or fails: unknown → 404,
 * suspended → 403. Handlers call this before touching tenant-scoped data; the thrown
 * [GatewayException] is mapped by StatusPages like any other.
 */
suspend fun ApplicationCall.resolveTenant(tenants: TenantRepository): Tenant {
    attributes.getOrNull(TenantAttributeKey)?.let { return it }
    val slug = parameters["tenantSlug"].orEmpty()
    val tenant = tenants.findBySlug(slug) ?: throw GatewayException.NotFound("Unknown tenant.")
    if (!tenant.isActive) throw GatewayException.Forbidden("Tenant is suspended.")
    attributes.put(TenantAttributeKey, tenant)
    return tenant
}

/** The tenant resolved earlier in this call by [resolveTenant]; 401 if none. */
fun ApplicationCall.tenant(): Tenant = attributes.getOrNull(TenantAttributeKey)
    ?: throw GatewayException.Unauthenticated("Tenant not resolved.")

fun ApplicationCall.tenantId(): TenantId = tenant().id

/** Mounts [build] under `/t/{tenantSlug}`. Handlers inside resolve the tenant explicitly. */
fun Route.tenantScoped(build: Route.() -> Unit): Route = route("/t/{tenantSlug}", build)

/** The OIDC issuer (`iss`) for a tenant: the base issuer plus its `/t/{slug}` prefix. */
fun tenantIssuer(baseIssuer: String, slug: String): String = "$baseIssuer/t/$slug"
