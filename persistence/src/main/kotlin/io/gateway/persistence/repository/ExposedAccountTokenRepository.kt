package io.gateway.persistence.repository

import io.gateway.domain.model.AccountToken
import io.gateway.domain.model.AccountTokenPurpose
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.AccountTokenRepository
import io.gateway.persistence.tables.AccountTokensTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [AccountTokenRepository] with atomic single-use consumption. Tenant-scoped. */
class ExposedAccountTokenRepository : AccountTokenRepository {

    override suspend fun insert(tenantId: TenantId, token: AccountToken) {
        tx {
            AccountTokensTable.insert { row ->
                row[tokenHash] = token.tokenHash
                row[AccountTokensTable.tenantId] = tenantId.value.toString()
                row[userId] = token.userId.value.toString()
                row[purpose] = token.purpose.name
                row[expiresAt] = token.expiresAt.toEpochMilliseconds()
                row[consumedAt] = token.consumedAt?.toEpochMilliseconds()
            }
        }
    }

    override suspend fun consume(
        tenantId: TenantId,
        tokenHash: String,
        purpose: AccountTokenPurpose,
        now: Instant,
    ): AccountToken? = tx {
        val tid = tenantId.value.toString()
        val row = AccountTokensTable.selectAll()
            .where {
                (AccountTokensTable.tokenHash eq tokenHash) and
                    (AccountTokensTable.tenantId eq tid) and
                    (AccountTokensTable.purpose eq purpose.name)
            }
            .singleOrNull() ?: return@tx null

        // Claim atomically: the UPDATE itself enforces unused + unexpired so two
        // concurrent callers can't both consume the same token.
        val nowMs = now.toEpochMilliseconds()
        val claimed = AccountTokensTable.update(
            {
                (AccountTokensTable.tokenHash eq tokenHash) and
                    (AccountTokensTable.tenantId eq tid) and
                    (AccountTokensTable.purpose eq purpose.name) and
                    AccountTokensTable.consumedAt.isNull() and
                    (AccountTokensTable.expiresAt greater nowMs)
            },
        ) {
            it[consumedAt] = nowMs
        }
        if (claimed == 0) return@tx null
        row.toToken()
    }

    override suspend fun deleteForUser(tenantId: TenantId, userId: UserId, purpose: AccountTokenPurpose) {
        tx {
            AccountTokensTable.deleteWhere {
                (AccountTokensTable.tenantId eq tenantId.value.toString()) and
                    (AccountTokensTable.userId eq userId.value.toString()) and
                    (AccountTokensTable.purpose eq purpose.name)
            }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toToken(): AccountToken = AccountToken(
        tokenHash = this[AccountTokensTable.tokenHash],
        userId = UserId.parse(this[AccountTokensTable.userId]),
        purpose = AccountTokenPurpose.valueOf(this[AccountTokensTable.purpose]),
        expiresAt = Instant.fromEpochMilliseconds(this[AccountTokensTable.expiresAt]),
        consumedAt = this[AccountTokensTable.consumedAt]?.let { Instant.fromEpochMilliseconds(it) },
    )
}
