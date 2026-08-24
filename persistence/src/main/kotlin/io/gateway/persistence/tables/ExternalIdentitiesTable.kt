package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** External-provider identity links; unique on (provider, subject). */
object ExternalIdentitiesTable : Table("external_identities") {
    val id = varchar("id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val userId = varchar("user_id", length = 36)
    val provider = varchar("provider", length = 64)
    val subject = varchar("subject", length = 255)
    val email = varchar("email", length = 320).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
