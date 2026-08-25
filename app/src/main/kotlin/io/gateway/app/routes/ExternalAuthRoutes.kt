package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.app.tenant.resolveTenant
import io.gateway.audit.AuditEventType
import io.gateway.audit.AuditLogger
import io.gateway.authexternal.AccountLinkingService
import io.gateway.authexternal.ExternalStateCodec
import io.gateway.authexternal.ProviderRegistry
import io.gateway.common.Base64Url
import io.gateway.common.GatewayException
import io.gateway.common.Sha256
import io.gateway.domain.repository.TenantRepository
import io.gateway.session.SessionService
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlin.time.Duration.Companion.hours

private const val STATE_COOKIE = "gw_ext_state"
private const val RETURN_COOKIE = "gw_ext_return"
private const val STATE_COOKIE_MAX_AGE = 600

/**
 * State/return cookie path, shared by `/start` (mounted under /t/{slug}) and the
 * tenant-agnostic `/callback`. Fixed so a single provider redirect URI serves
 * every tenant — the tenant travels in the signed state, not the URL.
 */
private const val EXTERNAL_COOKIE_PATH = "/api/auth/external"

/**
 * Tenant-scoped external-login entry points (mounted under /t/{slug}):
 * `/providers` lists configured IdPs; `/{provider}/start` redirects to the provider
 * with a signed state (carrying provider + tenant) + PKCE challenge in an HttpOnly
 * cookie. The matching `/callback` is registered globally by [externalCallbackRoutes].
 */
fun Route.externalAuthRoutes(
    registry: ProviderRegistry,
    stateCodec: ExternalStateCodec,
    tenants: TenantRepository,
    config: GatewayConfig,
) = route("/api/auth/external") {
    // Rate limiting is inherited from the parent /api/auth node (see authRoutes).

    // Providers actually configured (both client id + secret set). The SPA reads
    // this to render only usable buttons instead of the full known set.
    get("/providers") {
        call.respond(registry.ids().toList())
    }

    get("/{provider}/start") {
        val slug = call.resolveTenant(tenants).slug
        val id = call.parameters["provider"].orEmpty()
        val provider = registry.get(id) ?: throw GatewayException.NotFound("Unknown provider: $id")

        val issued = stateCodec.issue(id, slug)
        setStateCookie(call, config, EXTERNAL_COOKIE_PATH, issued.cookieValue)
        // Remember where to send the browser after login (e.g. a pending OIDC
        // authorize request), so external login returns there, not the default.
        safeReturnPath(call.request.queryParameters["redirect"])
            ?.let { setReturnCookie(call, config, EXTERNAL_COOKIE_PATH, it) }
        val challenge = Base64Url.encode(Sha256.hash(issued.codeVerifier))
        call.respondRedirect(provider.authorizeUrl(issued.state, challenge, callbackUri(config, id)))
    }
}

/**
 * Tenant-agnostic external-login callback, registered at the root (NOT under
 * /t/{slug}). The provider redirect URI is therefore one fixed path per provider,
 * `${issuer}/api/auth/external/{provider}/callback`, for every tenant — the tenant
 * is recovered from the signed state, not the URL.
 */
fun Route.externalCallbackRoutes(
    registry: ProviderRegistry,
    stateCodec: ExternalStateCodec,
    linking: AccountLinkingService,
    sessions: SessionService,
    tenants: TenantRepository,
    audit: AuditLogger,
    config: GatewayConfig,
) = route("/api/auth/external") {
    get("/{provider}/callback") {
        val id = call.parameters["provider"].orEmpty()
        val provider = registry.get(id) ?: throw GatewayException.NotFound("Unknown provider: $id")

        call.request.queryParameters["error"]?.let {
            throw GatewayException.Unauthenticated("Provider returned an error: $it")
        }
        val code = call.request.queryParameters["code"]
            ?: throw GatewayException.Validation("Missing authorization code.")
        val stateParam = call.request.queryParameters["state"]
            ?: throw GatewayException.Validation("Missing state.")
        val cookie = call.request.cookies[STATE_COOKIE]
            ?: throw GatewayException.Unauthenticated("Missing state cookie.")
        val verified = stateCodec.verify(cookie, stateParam)
            ?: throw GatewayException.Unauthenticated("Invalid or expired state.")
        if (verified.provider != id) throw GatewayException.Unauthenticated("Provider mismatch.")

        // Tenant comes from the signed state, not the path.
        val tenant = tenants.findBySlug(verified.tenantSlug)
            ?: throw GatewayException.NotFound("Unknown tenant.")
        if (!tenant.isActive) throw GatewayException.Forbidden("Tenant is suspended.")

        val profile = provider.exchange(code, verified.codeVerifier, callbackUri(config, id))
        val user = linking.resolve(tenant.id, profile)
        audit.record(
            tenant.id,
            AuditEventType.EXTERNAL_IDENTITY_LINKED,
            actor = user.id,
            ip = call.clientIp(),
            userAgent = call.userAgent(),
            detail = "provider=$id",
        )

        clearStateCookie(call, config, EXTERNAL_COOKIE_PATH)
        val issued = sessions.create(
            tenantId = tenant.id,
            userId = user.id,
            amr = setOf("ext"),
            ip = call.request.local.remoteHost,
            userAgent = call.request.headers["User-Agent"],
        )
        SessionCookies.set(call, config, issued.rawToken, config.sessionTtlHours.hours.inWholeSeconds)

        val target = call.request.cookies[RETURN_COOKIE]
            ?.let(::safeReturnPath)
            ?.let { originOf(config.postLoginRedirect) + it }
            ?: config.postLoginRedirect
        clearReturnCookie(call, config, EXTERNAL_COOKIE_PATH)
        call.respondRedirect(target)
    }
}

/**
 * Accept only a site-relative path (e.g. `/oauth2/authorize?...`). Rejects absolute
 * and scheme-relative URLs so the post-login redirect can never leave the frontend
 * origin — the path is later appended to a trusted origin.
 */
private fun safeReturnPath(raw: String?): String? {
    if (raw.isNullOrBlank() || !raw.startsWith("/")) return null
    if (raw.length > 1 && (raw[1] == '/' || raw[1] == '\\')) return null
    return raw
}

/** Scheme + host[:port] of a URL, dropping any path. */
private fun originOf(url: String): String {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return url.trimEnd('/')
    val rest = url.substring(schemeEnd + 3)
    val slash = rest.indexOf('/')
    return url.substring(0, schemeEnd + 3) + if (slash < 0) rest else rest.substring(0, slash)
}

private fun callbackUri(config: GatewayConfig, provider: String): String =
    "${config.issuer}/api/auth/external/$provider/callback"

private fun setStateCookie(call: ApplicationCall, config: GatewayConfig, path: String, value: String) {
    call.response.cookies.append(
        Cookie(
            name = STATE_COOKIE,
            value = value,
            encoding = CookieEncoding.RAW,
            httpOnly = true,
            secure = config.cookieSecure,
            path = path,
            maxAge = STATE_COOKIE_MAX_AGE,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

private fun clearStateCookie(call: ApplicationCall, config: GatewayConfig, path: String) {
    call.response.cookies.append(
        Cookie(
            name = STATE_COOKIE,
            value = "",
            encoding = CookieEncoding.RAW,
            httpOnly = true,
            secure = config.cookieSecure,
            path = path,
            maxAge = 0,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

private fun setReturnCookie(call: ApplicationCall, config: GatewayConfig, path: String, value: String) {
    call.response.cookies.append(
        Cookie(
            name = RETURN_COOKIE,
            value = value,
            encoding = CookieEncoding.URI_ENCODING,
            httpOnly = true,
            secure = config.cookieSecure,
            path = path,
            maxAge = STATE_COOKIE_MAX_AGE,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

private fun clearReturnCookie(call: ApplicationCall, config: GatewayConfig, path: String) {
    call.response.cookies.append(
        Cookie(
            name = RETURN_COOKIE,
            value = "",
            encoding = CookieEncoding.URI_ENCODING,
            httpOnly = true,
            secure = config.cookieSecure,
            path = path,
            maxAge = 0,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}
