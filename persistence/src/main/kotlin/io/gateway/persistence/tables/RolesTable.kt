package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Custom RBAC roles. `permissions` is newline-separated text. Unique slug per tenant. */
object RolesTable : Table("roles") {
    val id = varchar("id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val slug = varchar("slug", length = 64)
    val name = varchar("display_name", length = 128)
    val description = varchar("description", length = 512).nullable()
    val permissions = text("permissions")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
