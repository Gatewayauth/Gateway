package io.gateway.app.routes

import io.gateway.app.tenant.resolveTenant
import io.gateway.app.tenant.tenantIssuer
import io.gateway.domain.repository.TenantRepository
import io.gateway.oidc.DiscoveryDocument
import io.gateway.oidc.JwksProvider
import io.gateway.oidc.OidcConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Per-tenant OIDC discovery + JWKS. Mounted under `/t/{slug}`, so the discovery
 * document advertises the tenant issuer and its tenant-scoped endpoints, and JWKS
 * returns that tenant's key set.
 */
fun Route.oidcRoutes(config: OidcConfig, jwks: JwksProvider, tenants: TenantRepository) {
    get("/.well-known/openid-configuration") {
        val tenant = call.resolveTenant(tenants)
        call.respond(JsonElements.of(DiscoveryDocument.build(tenantIssuer(config.issuer, tenant.slug))))
    }

    get("/.well-known/jwks.json") {
        val tid = call.resolveTenant(tenants).id
        call.respond(JsonElements.of(jwks.jwks(tid)))
    }
}
