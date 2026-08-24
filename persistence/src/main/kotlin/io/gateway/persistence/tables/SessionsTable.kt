package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Server-side sessions. `token_hash` is the SHA-256 of the opaque cookie value. */
object SessionsTable : Table("sessions") {
    val id = varchar("id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val userId = varchar("user_id", length = 36)
    val tokenHash = varchar("token_hash", length = 64)
    val amr = text("amr") // space-separated auth methods
    val createdAt = long("created_at")
    val lastSeenAt = long("last_seen_at")
    val expiresAt = long("expires_at")
    val revokedAt = long("revoked_at").nullable()
    val ip = varchar("ip", length = 64).nullable()
    val userAgent = varchar("user_agent", length = 512).nullable()

    override val primaryKey = PrimaryKey(id)
}
