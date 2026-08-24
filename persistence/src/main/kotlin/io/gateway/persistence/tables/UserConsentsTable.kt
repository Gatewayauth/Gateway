package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Recorded user→client scope consents (composite PK on user + client). */
object UserConsentsTable : Table("user_consents") {
    val tenantId = varchar("tenant_id", length = 36)
    val userId = varchar("user_id", length = 36)
    val clientId = varchar("client_id", length = 200)
    val scopes = text("scopes")
    val grantedAt = long("granted_at")

    override val primaryKey = PrimaryKey(userId, clientId)
}
