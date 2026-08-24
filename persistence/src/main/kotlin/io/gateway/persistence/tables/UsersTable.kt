package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Maps the `users` table. Timestamps stored as epoch millis (BIGINT). */
object UsersTable : Table("users") {
    val id = varchar("id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val email = varchar("email", length = 320)
    val emailVerified = bool("email_verified")
    val displayName = varchar("display_name", length = 200).nullable()
    val status = varchar("status", length = 32)
    val mfaRequired = bool("mfa_required")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
