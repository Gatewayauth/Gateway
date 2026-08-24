package io.gateway.persistence.repository

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.CredentialRepository
import io.gateway.persistence.tables.CredentialsTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/** Exposed-backed [CredentialRepository] for Argon2 password hashes. */
class ExposedCredentialRepository : CredentialRepository {

    override suspend fun findPasswordHash(tenantId: TenantId, userId: UserId): String? = tx {
        CredentialsTable.selectAll()
            .where {
                (CredentialsTable.userId eq userId.value.toString()) and
                    (CredentialsTable.tenantId eq tenantId.value.toString())
            }
            .singleOrNull()
            ?.get(CredentialsTable.passwordHash)
    }

    override suspend fun upsertPasswordHash(tenantId: TenantId, userId: UserId, encodedHash: String) = tx {
        val uid = userId.value.toString()
        val tid = tenantId.value.toString()
        val now = Instant.now().toEpochMilli()
        val updated = CredentialsTable.update({
            (CredentialsTable.userId eq uid) and (CredentialsTable.tenantId eq tid)
        }) { row ->
            row[passwordHash] = encodedHash
            row[updatedAt] = now
        }
        if (updated == 0) {
            CredentialsTable.insert { row ->
                row[CredentialsTable.userId] = uid
                row[CredentialsTable.tenantId] = tid
                row[passwordHash] = encodedHash
                row[updatedAt] = now
            }
        }
        Unit
    }

    override suspend fun deletePassword(tenantId: TenantId, userId: UserId) = tx {
        CredentialsTable.deleteWhere {
            (CredentialsTable.userId eq userId.value.toString()) and
                (CredentialsTable.tenantId eq tenantId.value.toString())
        }
        Unit
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
}
