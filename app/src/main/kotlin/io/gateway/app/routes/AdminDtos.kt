package io.gateway.app.routes

import io.gateway.audit.AuditRecord
import io.gateway.domain.model.OAuthClient
import io.gateway.oidc.ClientRegistration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Admin management API payloads. Nested to keep one top-level type per file. */
object AdminDtos {

    @Serializable
    data class CreateClientRequest(
        val name: String,
        @SerialName("redirect_uris") val redirectUris: Set<String>,
        val scopes: Set<String> = setOf("openid", "profile", "email"),
        val public: Boolean = false,
        @SerialName("require_consent") val requireConsent: Boolean = true,
        @SerialName("required_roles") val requiredRoles: Set<String> = emptySet(),
    )

    /** Mutable fields for an existing client. `secretHash`/`createdAt`/`public` are not changed here. */
    @Serializable
    data class UpdateClientRequest(
        val name: String,
        @SerialName("redirect_uris") val redirectUris: Set<String>,
        val scopes: Set<String> = setOf("openid", "profile", "email"),
        @SerialName("require_consent") val requireConsent: Boolean = true,
        @SerialName("required_roles") val requiredRoles: Set<String> = emptySet(),
    )

    @Serializable
    data class ClientResponse(
        @SerialName("client_id") val clientId: String,
        val name: String,
        val public: Boolean,
        @SerialName("redirect_uris") val redirectUris: Set<String>,
        val scopes: Set<String>,
        @SerialName("required_roles") val requiredRoles: Set<String>,
        @SerialName("require_consent") val requireConsent: Boolean,
        @SerialName("client_secret") val clientSecret: String? = null,
    ) {
        companion object {
            fun of(registration: ClientRegistration): ClientResponse = with(registration.client) {
                ClientResponse(
                    clientId = id.value,
                    name = name,
                    public = public,
                    redirectUris = redirectUris,
                    scopes = allowedScopes,
                    requiredRoles = requiredRoles,
                    requireConsent = requireConsent,
                    clientSecret = registration.plaintextSecret,
                )
            }
        }
    }

    @Serializable
    data class ClientSummary(
        @SerialName("client_id") val clientId: String,
        val name: String,
        val public: Boolean,
        @SerialName("redirect_uris") val redirectUris: Set<String>,
        val scopes: Set<String>,
        @SerialName("required_roles") val requiredRoles: Set<String>,
        @SerialName("require_consent") val requireConsent: Boolean,
    ) {
        companion object {
            fun of(client: OAuthClient): ClientSummary = ClientSummary(
                clientId = client.id.value,
                name = client.name,
                public = client.public,
                redirectUris = client.redirectUris,
                scopes = client.allowedScopes,
                requiredRoles = client.requiredRoles,
                requireConsent = client.requireConsent,
            )
        }
    }

    @Serializable
    data class UserStatusRequest(val status: String)

    @Serializable
    data class UserRoleRequest(val role: String)

    @Serializable
    data class AuditEntry(
        val id: String,
        val at: Long,
        @SerialName("actor_user_id") val actorUserId: String?,
        @SerialName("actor_label") val actorLabel: String?,
        @SerialName("event_type") val eventType: String,
        val ip: String?,
        val detail: String?,
    ) {
        companion object {
            fun of(record: AuditRecord): AuditEntry = AuditEntry(
                id = record.id,
                at = record.at.toEpochMilliseconds(),
                actorUserId = record.actorUserId,
                actorLabel = record.actorLabel,
                eventType = record.eventType,
                ip = record.ip,
                detail = record.detail,
            )
        }
    }
}
