package io.gateway.app.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Liveness/readiness endpoint for orchestrators. */
fun Route.healthRoutes() {
    get("/healthz") {
        call.respond(AuthDtos.MessageResponse("ok"))
    }
}
