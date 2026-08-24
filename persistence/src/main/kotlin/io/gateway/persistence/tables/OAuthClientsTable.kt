package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Registered OAuth2 relying parties. Multi-valued fields (redirect URIs, scopes,
 * grant types) are stored newline/space-separated text for v1 simplicity.
 */
object OAuthClientsTable : Table("oauth_clients") {
    val id = varchar("client_id", length = 200)
    val tenantId = varchar("tenant_id", length = 36)
    val name = varchar("client_name", length = 200)
    val public = bool("is_public")
    val secretHash = varchar("secret_hash", length = 128).nullable()
    val redirectUris = text("redirect_uris")
    val allowedScopes = text("allowed_scopes")
    val grantTypes = text("grant_types")
    val requirePkce = bool("require_pkce")
    val requireConsent = bool("require_consent")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
