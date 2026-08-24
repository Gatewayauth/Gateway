package io.gateway.app.plugins

import io.gateway.app.routes.ErrorResponse
import io.gateway.common.GatewayException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("io.gateway.app.ErrorHandler")

/** Maps Gateway's typed exceptions to HTTP responses with a uniform error body. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<GatewayException> { call, cause ->
            call.respond(statusFor(cause), ErrorResponse(cause.code, cause.message ?: cause.code))
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Unexpected error."))
        }
    }
}

private fun statusFor(cause: GatewayException): HttpStatusCode = when (cause) {
    is GatewayException.Validation -> HttpStatusCode.BadRequest
    is GatewayException.Unauthenticated -> HttpStatusCode.Unauthorized
    is GatewayException.Forbidden -> HttpStatusCode.Forbidden
    is GatewayException.NotFound -> HttpStatusCode.NotFound
    is GatewayException.Conflict -> HttpStatusCode.Conflict
    is GatewayException.RateLimited -> HttpStatusCode.TooManyRequests
}
