package io.gateway.persistence.repository

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.RecoveryCodeRepository
import io.gateway.persistence.tables.RecoveryCodesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/** Exposed-backed [RecoveryCodeRepository] with atomic single-use consumption. Tenant-scoped. */
class ExposedRecoveryCodeRepository : RecoveryCodeRepository {

    override suspend fun replaceAll(tenantId: TenantId, userId: UserId, codeHashes: List<String>, now: Instant) {
        tx {
            val uid = userId.value.toString()
            val tid = tenantId.value.toString()
            RecoveryCodesTable.deleteWhere {
                (RecoveryCodesTable.userId eq uid) and (RecoveryCodesTable.tenantId eq tid)
            }
            codeHashes.forEach { hash ->
                RecoveryCodesTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[RecoveryCodesTable.tenantId] = tid
                    it[RecoveryCodesTable.userId] = uid
                    it[codeHash] = hash
                    it[usedAt] = null
                    it[createdAt] = now.toEpochMilliseconds()
                }
            }
        }
    }

    override suspend fun consume(tenantId: TenantId, userId: UserId, codeHash: String, now: Instant): Boolean = tx {
        val updated = RecoveryCodesTable.update(
            {
                (RecoveryCodesTable.tenantId eq tenantId.value.toString()) and
                    (RecoveryCodesTable.userId eq userId.value.toString()) and
                    (RecoveryCodesTable.codeHash eq codeHash) and
                    RecoveryCodesTable.usedAt.isNull()
            },
        ) {
            it[usedAt] = now.toEpochMilliseconds()
        }
        updated > 0
    }

    override suspend fun deleteAll(tenantId: TenantId, userId: UserId) {
        tx {
            RecoveryCodesTable.deleteWhere {
                (RecoveryCodesTable.userId eq userId.value.toString()) and
                    (RecoveryCodesTable.tenantId eq tenantId.value.toString())
            }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
}
