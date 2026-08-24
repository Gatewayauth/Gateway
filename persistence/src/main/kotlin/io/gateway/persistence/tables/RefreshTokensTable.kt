package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Refresh tokens (hash only) with rotation-chain [familyId]. */
object RefreshTokensTable : Table("refresh_tokens") {
    val tokenHash = varchar("token_hash", length = 64)
    val tenantId = varchar("tenant_id", length = 36)
    val familyId = varchar("family_id", length = 64)
    val clientId = varchar("client_id", length = 200)
    val userId = varchar("user_id", length = 36)
    val scopes = text("scopes")
    val issuedAt = long("issued_at")
    val expiresAt = long("expires_at")
    val rotatedAt = long("rotated_at").nullable()
    val revokedAt = long("revoked_at").nullable()

    override val primaryKey = PrimaryKey(tokenHash)
}
