package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Tenants. [slug] is the unique URL segment; [id] is the stable key on scoped rows. */
object TenantsTable : Table("tenants") {
    val id = varchar("id", length = 36)
    val slug = varchar("slug", length = 64)
    val name = varchar("tenant_name", length = 200)
    val status = varchar("status", length = 32)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
