package io.gateway.app.routes

import io.gateway.app.config.GatewayConfig
import io.gateway.app.tenant.resolveTenant
import io.gateway.app.tenant.tenantIssuer
import io.gateway.audit.AuditEventType
import io.gateway.audit.AuditLogger
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.oidc.AccessTokenVerifier
import io.gateway.oidc.AuthorizationRequest
import io.gateway.oidc.AuthorizationService
import io.gateway.oidc.ClientAuthenticator
import io.gateway.oidc.ConsentRequiredException
import io.gateway.oidc.ConsentService
import io.gateway.oidc.OAuthException
import io.gateway.oidc.TokenResult
import io.gateway.oidc.TokenService
import io.gateway.session.SessionService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.Base64

/** OIDC authorize / token / userinfo endpoints. */
fun Route.oauth2Routes(
    sessions: SessionService,
    users: UserRepository,
    tenants: TenantRepository,
    authorization: AuthorizationService,
    tokens: TokenService,
    clientAuth: ClientAuthenticator,
    accessTokens: AccessTokenVerifier,
    consent: ConsentService,
    audit: AuditLogger,
    config: GatewayConfig,
) = route("/oauth2") {
    get("/authorize") {
        val tid = call.resolveTenant(tenants).id
        val request = call.authorizationRequest()
        val session = SessionCookies.read(call, config)?.let { sessions.resolve(tid, it) }
            ?: return@get call.respondLoginRequired()
        try {
            call.respondRedirect(authorization.authorize(tid, request, session.userId), permanent = false)
        } catch (e: ConsentRequiredException) {
            call.respond(
                OAuthDtos.ConsentRequired(clientId = e.clientId, clientName = e.clientName, scopes = e.scopes.toList()),
            )
        } catch (e: OAuthException) {
            call.respondOAuthError(e)
        }
    }

    post("/consent") {
        val tid = call.resolveTenant(tenants).id
        val session = SessionCookies.read(call, config)?.let { sessions.resolve(tid, it) }
            ?: return@post call.respondLoginRequired()
        val body = call.receive<OAuthDtos.ConsentRequest>()
        try {
            consent.grant(tid, session.userId, body.clientId, body.scopes)
            call.respond(AuthDtos.MessageResponse("Consent recorded."))
        } catch (e: OAuthException) {
            call.respondOAuthError(e)
        }
    }

    post("/token") {
        val tenant = call.resolveTenant(tenants)
        val tid = tenant.id
        val issuer = tenantIssuer(config.issuer, tenant.slug)
        val params = call.receiveParameters()
        try {
            val (clientId, clientSecret) = call.clientCredentials(params)
            val client = clientAuth.authenticate(tid, clientId, clientSecret)
            val result = when (val grantType = params["grant_type"]) {
                "authorization_code" -> tokens.authorizationCodeGrant(
                    tenantId = tid,
                    issuer = issuer,
                    client = client,
                    code = params["code"].orEmpty(),
                    redirectUri = params["redirect_uri"].orEmpty(),
                    codeVerifier = params["code_verifier"],
                )
                "refresh_token" -> tokens.refreshTokenGrant(tid, issuer, client, params["refresh_token"].orEmpty())
                else -> throw OAuthException.unsupportedGrantType("Unsupported grant_type: $grantType")
            }
            audit.record(
                tid,
                AuditEventType.TOKEN_ISSUED,
                actor = null,
                ip = call.clientIp(),
                userAgent = call.userAgent(),
                detail = "client_id=${client.id}",
            )
            call.respond(result.toResponse())
        } catch (e: OAuthException) {
            call.respondOAuthError(e)
        }
    }

    get("/userinfo") {
        val tenant = call.resolveTenant(tenants)
        val tid = tenant.id
        val header = call.request.headers["Authorization"]
        if (header == null || !header.startsWith("Bearer ")) {
            return@get call.respond(
                HttpStatusCode.Unauthorized,
                OAuthDtos.ErrorResponse("invalid_token", "Missing bearer token."),
            )
        }
        try {
            val issuer = tenantIssuer(config.issuer, tenant.slug)
            val claims = accessTokens.verify(tid, issuer, header.removePrefix("Bearer ").trim())
            val user = users.findById(tid, UserId.parse(claims.subject))
                ?: throw OAuthException.invalidGrant("Unknown subject.")
            call.respond(user.toUserInfo(claims.scopes))
        } catch (e: OAuthException) {
            call.respond(HttpStatusCode.Unauthorized, OAuthDtos.ErrorResponse(e.error, e.description))
        }
    }
}

private fun ApplicationCall.authorizationRequest(): AuthorizationRequest {
    val q = request.queryParameters
    return AuthorizationRequest(
        clientId = q["client_id"].orEmpty(),
        redirectUri = q["redirect_uri"].orEmpty(),
        responseType = q["response_type"].orEmpty(),
        scope = q["scope"].orEmpty(),
        state = q["state"],
        nonce = q["nonce"],
        codeChallenge = q["code_challenge"],
        codeChallengeMethod = q["code_challenge_method"],
    )
}

private suspend fun ApplicationCall.respondLoginRequired() = respond(
    HttpStatusCode.Unauthorized,
    OAuthDtos.ErrorResponse("login_required", "Authentication required."),
)

/** Only release claims the granted scopes cover (OIDC userinfo semantics). */
private fun io.gateway.domain.model.User.toUserInfo(scopes: Set<String>) = OAuthDtos.UserInfo(
    sub = id.toString(),
    email = if ("email" in scopes) email else null,
    emailVerified = if ("email" in scopes) emailVerified else null,
    name = if ("profile" in scopes) displayName else null,
)

private fun TokenResult.toResponse() = OAuthDtos.TokenResponse(
    accessToken = accessToken,
    tokenType = tokenType,
    expiresIn = expiresInSeconds,
    scope = scope,
    idToken = idToken,
    refreshToken = refreshToken,
)

/** Extracts client credentials from HTTP Basic auth or the request body. */
private fun ApplicationCall.clientCredentials(params: Parameters): Pair<String, String?> {
    val basic = request.headers["Authorization"]?.takeIf { it.startsWith("Basic ") }
    if (basic != null) {
        val decoded = String(Base64.getDecoder().decode(basic.removePrefix("Basic ").trim()))
        val id = decoded.substringBefore(':')
        val secret = decoded.substringAfter(':', "")
        return id to secret.ifEmpty { null }
    }
    return params["client_id"].orEmpty() to params["client_secret"]
}

private suspend fun ApplicationCall.respondOAuthError(e: OAuthException) {
    val status = if (e.unauthorized) HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest
    respond(status, OAuthDtos.ErrorResponse(e.error, e.description))
}
