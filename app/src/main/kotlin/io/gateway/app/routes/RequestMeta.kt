package io.gateway.app.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.userAgent

/** Client IP + User-Agent extraction for audit records. */
internal fun ApplicationCall.clientIp(): String = request.origin.remoteHost

internal fun ApplicationCall.userAgent(): String? = request.userAgent()
