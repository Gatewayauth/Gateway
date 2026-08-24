package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Argon2 password hashes, one row per user. */
object CredentialsTable : Table("credentials") {
    val userId = varchar("user_id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val passwordHash = text("password_hash")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId)
}
