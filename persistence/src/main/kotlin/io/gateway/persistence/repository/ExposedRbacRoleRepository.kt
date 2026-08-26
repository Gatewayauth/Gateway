package io.gateway.persistence.repository

import io.gateway.domain.model.RbacRole
import io.gateway.domain.model.RoleId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.RbacRoleRepository
import io.gateway.persistence.tables.RolesTable
import io.gateway.persistence.tables.UserRolesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [RbacRoleRepository]. Permissions are newline-separated text. Tenant-scoped. */
class ExposedRbacRoleRepository : RbacRoleRepository {

    override suspend fun list(tenantId: TenantId): List<RbacRole> = tx {
        RolesTable.selectAll()
            .where { RolesTable.tenantId eq tenantId.value.toString() }
            .orderBy(RolesTable.slug to SortOrder.ASC)
            .map { it.toRole() }
    }

    override suspend fun findById(tenantId: TenantId, id: RoleId): RbacRole? = tx {
        RolesTable.selectAll()
            .where { (RolesTable.id eq id.value.toString()) and (RolesTable.tenantId eq tenantId.value.toString()) }
            .singleOrNull()?.toRole()
    }

    override suspend fun findBySlug(tenantId: TenantId, slug: String): RbacRole? = tx {
        RolesTable.selectAll()
            .where { (RolesTable.slug eq slug) and (RolesTable.tenantId eq tenantId.value.toString()) }
            .singleOrNull()?.toRole()
    }

    override suspend fun insert(role: RbacRole): RbacRole = tx {
        RolesTable.insert { row ->
            row[id] = role.id.value.toString()
            row[tenantId] = role.tenantId.value.toString()
            row[slug] = role.slug
            row[name] = role.name
            row[description] = role.description
            row[permissions] = encodePermissions(role.permissions)
            row[createdAt] = role.createdAt.toEpochMilliseconds()
        }
        role
    }

    override suspend fun update(role: RbacRole): RbacRole = tx {
        RolesTable.update({
            (RolesTable.id eq role.id.value.toString()) and (RolesTable.tenantId eq role.tenantId.value.toString())
        }) { row ->
            // slug is immutable — intentionally not updated.
            row[name] = role.name
            row[description] = role.description
            row[permissions] = encodePermissions(role.permissions)
        }
        role
    }

    override suspend fun delete(tenantId: TenantId, id: RoleId) {
        tx {
            // user_roles rows cascade via FK.
            RolesTable.deleteWhere {
                (RolesTable.id eq id.value.toString()) and (RolesTable.tenantId eq tenantId.value.toString())
            }
        }
    }

    override suspend fun listForUser(tenantId: TenantId, userId: UserId): List<RbacRole> = tx {
        val roleIds = UserRolesTable.selectAll()
            .where {
                (UserRolesTable.userId eq userId.value.toString()) and
                    (UserRolesTable.tenantId eq tenantId.value.toString())
            }
            .map { it[UserRolesTable.roleId] }
        if (roleIds.isEmpty()) {
            emptyList()
        } else {
            RolesTable.selectAll()
                .where {
                    (RolesTable.id inList roleIds) and (RolesTable.tenantId eq tenantId.value.toString())
                }
                .orderBy(RolesTable.slug to SortOrder.ASC)
                .map { it.toRole() }
        }
    }

    override suspend fun setUserRoles(tenantId: TenantId, userId: UserId, roleIds: Set<RoleId>) {
        tx {
            val uid = userId.value.toString()
            val tid = tenantId.value.toString()
            UserRolesTable.deleteWhere {
                (UserRolesTable.userId eq uid) and (UserRolesTable.tenantId eq tid)
            }
            roleIds.forEach { rid ->
                UserRolesTable.insert {
                    it[UserRolesTable.tenantId] = tid
                    it[UserRolesTable.userId] = uid
                    it[UserRolesTable.roleId] = rid.value.toString()
                }
            }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun encodePermissions(perms: Set<String>): String = perms.joinToString("\n")

    private fun decodePermissions(raw: String): Set<String> =
        raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun ResultRow.toRole(): RbacRole = RbacRole(
        id = RoleId.parse(this[RolesTable.id]),
        tenantId = TenantId.parse(this[RolesTable.tenantId]),
        slug = this[RolesTable.slug],
        name = this[RolesTable.name],
        description = this[RolesTable.description],
        permissions = decodePermissions(this[RolesTable.permissions]),
        createdAt = Instant.fromEpochMilliseconds(this[RolesTable.createdAt]),
    )
}
