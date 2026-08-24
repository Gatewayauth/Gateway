package io.gateway.persistence.repository

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.RefreshTokenRecord
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.RefreshTokenRepository
import io.gateway.persistence.tables.RefreshTokensTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [RefreshTokenRepository]. Every query is scoped by tenant_id. */
class ExposedRefreshTokenRepository : RefreshTokenRepository {

    override suspend fun insert(tenantId: TenantId, token: RefreshTokenRecord) {
        tx {
            RefreshTokensTable.insert { row ->
                row[tokenHash] = token.tokenHash
                row[RefreshTokensTable.tenantId] = tenantId.value.toString()
                row[familyId] = token.familyId
                row[clientId] = token.clientId.value
                row[userId] = token.userId.value.toString()
                row[scopes] = token.scopes.joinToString(" ")
                row[issuedAt] = token.issuedAt.toEpochMilliseconds()
                row[expiresAt] = token.expiresAt.toEpochMilliseconds()
                row[rotatedAt] = token.rotatedAt?.toEpochMilliseconds()
                row[revokedAt] = token.revokedAt?.toEpochMilliseconds()
            }
        }
    }

    override suspend fun findByHash(tenantId: TenantId, tokenHash: String): RefreshTokenRecord? = tx {
        RefreshTokensTable.selectAll()
            .where {
                (RefreshTokensTable.tokenHash eq tokenHash) and
                    (RefreshTokensTable.tenantId eq tenantId.value.toString())
            }
            .singleOrNull()
            ?.toRecord()
    }

    override suspend fun markRotated(tenantId: TenantId, tokenHash: String, now: Instant): Boolean = tx {
        val updated = RefreshTokensTable.update({
            (RefreshTokensTable.tokenHash eq tokenHash) and
                (RefreshTokensTable.tenantId eq tenantId.value.toString()) and
                RefreshTokensTable.rotatedAt.isNull()
        }) {
            it[rotatedAt] = now.toEpochMilliseconds()
        }
        updated > 0
    }

    override suspend fun revokeFamily(tenantId: TenantId, familyId: String, now: Instant) {
        tx {
            RefreshTokensTable.update({
                (RefreshTokensTable.familyId eq familyId) and
                    (RefreshTokensTable.tenantId eq tenantId.value.toString()) and
                    RefreshTokensTable.revokedAt.isNull()
            }) {
                it[revokedAt] = now.toEpochMilliseconds()
            }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toRecord(): RefreshTokenRecord = RefreshTokenRecord(
        tokenHash = this[RefreshTokensTable.tokenHash],
        familyId = this[RefreshTokensTable.familyId],
        clientId = ClientId(this[RefreshTokensTable.clientId]),
        userId = UserId.parse(this[RefreshTokensTable.userId]),
        scopes = this[RefreshTokensTable.scopes].split(" ").filter { it.isNotBlank() }.toSet(),
        issuedAt = Instant.fromEpochMilliseconds(this[RefreshTokensTable.issuedAt]),
        expiresAt = Instant.fromEpochMilliseconds(this[RefreshTokensTable.expiresAt]),
        rotatedAt = this[RefreshTokensTable.rotatedAt]?.let { Instant.fromEpochMilliseconds(it) },
        revokedAt = this[RefreshTokensTable.revokedAt]?.let { Instant.fromEpochMilliseconds(it) },
    )
}
