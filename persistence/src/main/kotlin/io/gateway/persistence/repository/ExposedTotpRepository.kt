package io.gateway.persistence.repository

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.TotpEnrollment
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.TotpRepository
import io.gateway.persistence.tables.MfaTotpTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [TotpRepository]. Every query is scoped by tenant_id. */
class ExposedTotpRepository : TotpRepository {

    override suspend fun find(tenantId: TenantId, userId: UserId): TotpEnrollment? = tx {
        val uid = userId.value.toString()
        val tid = tenantId.value.toString()
        MfaTotpTable.selectAll()
            .where { (MfaTotpTable.userId eq uid) and (MfaTotpTable.tenantId eq tid) }
            .singleOrNull()?.let { row ->
                TotpEnrollment(
                    userId = userId,
                    secretEnc = row[MfaTotpTable.secretEnc],
                    confirmedAt = row[MfaTotpTable.confirmedAt]?.let { Instant.fromEpochMilliseconds(it) },
                    createdAt = Instant.fromEpochMilliseconds(row[MfaTotpTable.createdAt]),
                )
            }
    }

    override suspend fun upsert(tenantId: TenantId, enrollment: TotpEnrollment) {
        tx {
            val uid = enrollment.userId.value.toString()
            val tid = tenantId.value.toString()
            val updated = MfaTotpTable.update({ (MfaTotpTable.userId eq uid) and (MfaTotpTable.tenantId eq tid) }) {
                it[secretEnc] = enrollment.secretEnc
                it[confirmedAt] = enrollment.confirmedAt?.toEpochMilliseconds()
            }
            if (updated == 0) {
                MfaTotpTable.insert {
                    it[userId] = uid
                    it[MfaTotpTable.tenantId] = tid
                    it[secretEnc] = enrollment.secretEnc
                    it[confirmedAt] = enrollment.confirmedAt?.toEpochMilliseconds()
                    it[createdAt] = enrollment.createdAt.toEpochMilliseconds()
                }
            }
        }
    }

    override suspend fun confirm(tenantId: TenantId, userId: UserId, confirmedAt: Instant) {
        tx {
            val uid = userId.value.toString()
            val tid = tenantId.value.toString()
            MfaTotpTable.update({ (MfaTotpTable.userId eq uid) and (MfaTotpTable.tenantId eq tid) }) {
                it[MfaTotpTable.confirmedAt] = confirmedAt.toEpochMilliseconds()
            }
        }
    }

    override suspend fun delete(tenantId: TenantId, userId: UserId) {
        tx {
            val uid = userId.value.toString()
            val tid = tenantId.value.toString()
            MfaTotpTable.deleteWhere { (MfaTotpTable.userId eq uid) and (MfaTotpTable.tenantId eq tid) }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
}
