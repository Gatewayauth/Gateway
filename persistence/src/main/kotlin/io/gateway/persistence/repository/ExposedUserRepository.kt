package io.gateway.persistence.repository

import io.gateway.domain.model.Role
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.repository.UserRepository
import io.gateway.persistence.tables.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [UserRepository]. Every query is scoped by tenant_id. */
class ExposedUserRepository : UserRepository {

    override suspend fun findById(tenantId: TenantId, id: UserId): User? = tx {
        UsersTable.selectAll()
            .where { (UsersTable.id eq id.value.toString()) and (UsersTable.tenantId eq tenantId.value.toString()) }
            .singleOrNull()?.toUser()
    }

    override suspend fun findByEmail(tenantId: TenantId, email: String): User? = tx {
        UsersTable.selectAll()
            .where { (UsersTable.email eq email.lowercase()) and (UsersTable.tenantId eq tenantId.value.toString()) }
            .singleOrNull()?.toUser()
    }

    override suspend fun insert(tenantId: TenantId, user: User): User = tx {
        UsersTable.insert { row ->
            row[id] = user.id.value.toString()
            row[UsersTable.tenantId] = tenantId.value.toString()
            row[email] = user.email.lowercase()
            row[emailVerified] = user.emailVerified
            row[displayName] = user.displayName
            row[status] = user.status.name
            row[mfaRequired] = user.mfaRequired
            row[role] = user.role.name
            row[isSuperAdmin] = user.superAdmin
            row[createdAt] = user.createdAt.toEpochMilliseconds()
            row[updatedAt] = user.updatedAt.toEpochMilliseconds()
        }
        user
    }

    override suspend fun update(tenantId: TenantId, user: User): User = tx {
        UsersTable.update({
            (UsersTable.id eq user.id.value.toString()) and (UsersTable.tenantId eq tenantId.value.toString())
        }) { row ->
            row[email] = user.email.lowercase()
            row[emailVerified] = user.emailVerified
            row[displayName] = user.displayName
            row[status] = user.status.name
            row[mfaRequired] = user.mfaRequired
            row[role] = user.role.name
            row[isSuperAdmin] = user.superAdmin
            row[updatedAt] = user.updatedAt.toEpochMilliseconds()
        }
        user
    }

    override suspend fun list(tenantId: TenantId, limit: Int, offset: Long): List<User> = tx {
        UsersTable.selectAll()
            .where { UsersTable.tenantId eq tenantId.value.toString() }
            .orderBy(UsersTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toUser() }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toUser(): User = User(
        id = UserId.parse(this[UsersTable.id]),
        email = this[UsersTable.email],
        emailVerified = this[UsersTable.emailVerified],
        displayName = this[UsersTable.displayName],
        status = UserStatus.valueOf(this[UsersTable.status]),
        mfaRequired = this[UsersTable.mfaRequired],
        role = Role.valueOf(this[UsersTable.role]),
        superAdmin = this[UsersTable.isSuperAdmin],
        createdAt = Instant.fromEpochMilliseconds(this[UsersTable.createdAt]),
        updatedAt = Instant.fromEpochMilliseconds(this[UsersTable.updatedAt]),
    )
}
