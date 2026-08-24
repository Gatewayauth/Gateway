package io.gateway.persistence.repository

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.ConsentRepository
import io.gateway.domain.time.Clock
import io.gateway.persistence.tables.UserConsentsTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [ConsentRepository]. Stored scopes are unioned on each grant. Tenant-scoped. */
class ExposedConsentRepository(private val clock: Clock) : ConsentRepository {

    override suspend fun hasConsent(
        tenantId: TenantId,
        userId: UserId,
        clientId: ClientId,
        scopes: Set<String>,
    ): Boolean = tx {
        storedScopes(tenantId, userId, clientId).containsAll(scopes)
    }

    override suspend fun grant(tenantId: TenantId, userId: UserId, clientId: ClientId, scopes: Set<String>) {
        tx {
            val uid = userId.value.toString()
            val cid = clientId.value
            val tid = tenantId.value.toString()
            val merged = (storedScopes(tenantId, userId, clientId) + scopes).joinToString(" ")
            val updated = UserConsentsTable.update(
                {
                    (UserConsentsTable.tenantId eq tid) and
                        (UserConsentsTable.userId eq uid) and
                        (UserConsentsTable.clientId eq cid)
                },
            ) {
                it[UserConsentsTable.scopes] = merged
                it[grantedAt] = clock.now().toEpochMilliseconds()
            }
            if (updated == 0) {
                UserConsentsTable.insert {
                    it[UserConsentsTable.tenantId] = tid
                    it[UserConsentsTable.userId] = uid
                    it[UserConsentsTable.clientId] = cid
                    it[UserConsentsTable.scopes] = merged
                    it[grantedAt] = clock.now().toEpochMilliseconds()
                }
            }
        }
    }

    private fun storedScopes(tenantId: TenantId, userId: UserId, clientId: ClientId): Set<String> =
        UserConsentsTable.selectAll()
            .where {
                (UserConsentsTable.tenantId eq tenantId.value.toString()) and
                    (UserConsentsTable.userId eq userId.value.toString()) and
                    (UserConsentsTable.clientId eq clientId.value)
            }
            .singleOrNull()
            ?.get(UserConsentsTable.scopes)
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
}
