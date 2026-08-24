package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Single-use account tokens (email verify / password reset), stored hashed. */
object AccountTokensTable : Table("account_tokens") {
    val tokenHash = varchar("token_hash", length = 64)
    val tenantId = varchar("tenant_id", length = 36)
    val userId = varchar("user_id", length = 36)
    val purpose = varchar("purpose", length = 32)
    val expiresAt = long("expires_at")
    val consumedAt = long("consumed_at").nullable()

    override val primaryKey = PrimaryKey(tokenHash)
}
