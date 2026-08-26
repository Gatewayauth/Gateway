package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Join of users to custom RBAC roles (composite PK on user + role). */
object UserRolesTable : Table("user_roles") {
    val tenantId = varchar("tenant_id", length = 36)
    val userId = varchar("user_id", length = 36)
    val roleId = varchar("role_id", length = 36)

    override val primaryKey = PrimaryKey(userId, roleId)
}
