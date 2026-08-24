package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall

/**
 * Session cookie helpers. The cookie is HttpOnly + SameSite=Lax and (in prod)
 * Secure, so it is never exposed to JS and not sent on cross-site POSTs.
 */
object SessionCookies {

    fun set(call: ApplicationCall, config: GatewayConfig, rawToken: String, maxAgeSeconds: Long) {
        call.response.cookies.append(
            Cookie(
                name = config.sessionCookieName,
                value = rawToken,
                encoding = CookieEncoding.RAW,
                httpOnly = true,
                secure = config.cookieSecure,
                path = "/",
                maxAge = maxAgeSeconds.toInt(),
                extensions = mapOf("SameSite" to "Lax"),
            ),
        )
    }

    fun clear(call: ApplicationCall, config: GatewayConfig) {
        call.response.cookies.append(
            Cookie(
                name = config.sessionCookieName,
                value = "",
                encoding = CookieEncoding.RAW,
                httpOnly = true,
                secure = config.cookieSecure,
                path = "/",
                maxAge = 0,
                extensions = mapOf("SameSite" to "Lax"),
            ),
        )
    }

    fun read(call: ApplicationCall, config: GatewayConfig): String? =
        call.request.cookies[config.sessionCookieName]?.takeIf { it.isNotBlank() }
}
