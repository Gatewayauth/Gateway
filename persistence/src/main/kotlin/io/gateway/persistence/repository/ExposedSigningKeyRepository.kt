package io.gateway.persistence.repository

import io.gateway.domain.model.SigningKeyRecord
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.SigningKeyRepository
import io.gateway.persistence.tables.SigningKeysTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [SigningKeyRepository]. Every query is scoped by tenant_id. */
class ExposedSigningKeyRepository : SigningKeyRepository {

    override suspend fun all(tenantId: TenantId): List<SigningKeyRecord> = tx {
        SigningKeysTable.selectAll()
            .where { SigningKeysTable.tenantId eq tenantId.value.toString() }
            .map { it.toRecord() }
    }

    override suspend fun insert(tenantId: TenantId, record: SigningKeyRecord) {
        tx {
            SigningKeysTable.insert { row ->
                row[kid] = record.kid
                row[SigningKeysTable.tenantId] = tenantId.value.toString()
                row[algorithm] = record.algorithm
                row[publicJwk] = record.publicJwk
                row[privateKeyEnc] = record.privateKeyEnc
                row[active] = record.active
                row[createdAt] = record.createdAt.toEpochMilliseconds()
                row[expiresAt] = record.expiresAt?.toEpochMilliseconds()
            }
        }
    }

    override suspend fun retire(tenantId: TenantId, kid: String, expiresAt: Instant) {
        tx {
            SigningKeysTable.update({
                (SigningKeysTable.kid eq kid) and (SigningKeysTable.tenantId eq tenantId.value.toString())
            }) {
                it[active] = false
                it[SigningKeysTable.expiresAt] = expiresAt.toEpochMilliseconds()
            }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toRecord(): SigningKeyRecord = SigningKeyRecord(
        kid = this[SigningKeysTable.kid],
        algorithm = this[SigningKeysTable.algorithm],
        publicJwk = this[SigningKeysTable.publicJwk],
        privateKeyEnc = this[SigningKeysTable.privateKeyEnc],
        active = this[SigningKeysTable.active],
        createdAt = Instant.fromEpochMilliseconds(this[SigningKeysTable.createdAt]),
        expiresAt = this[SigningKeysTable.expiresAt]?.let { Instant.fromEpochMilliseconds(it) },
    )
}
