package io.gateway.persistence.repository

import io.gateway.domain.model.ExternalIdentity
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.ExternalIdentityRepository
import io.gateway.persistence.tables.ExternalIdentitiesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/** Exposed-backed [ExternalIdentityRepository]. Every query is scoped by tenant_id. */
class ExposedExternalIdentityRepository : ExternalIdentityRepository {

    override suspend fun findByProviderSubject(
        tenantId: TenantId,
        provider: String,
        subject: String,
    ): ExternalIdentity? = tx {
        ExternalIdentitiesTable.selectAll()
            .where {
                (ExternalIdentitiesTable.tenantId eq tenantId.value.toString()) and
                    (ExternalIdentitiesTable.provider eq provider) and
                    (ExternalIdentitiesTable.subject eq subject)
            }
            .singleOrNull()
            ?.toIdentity()
    }

    override suspend fun listForUser(tenantId: TenantId, userId: UserId): List<ExternalIdentity> = tx {
        ExternalIdentitiesTable.selectAll()
            .where {
                (ExternalIdentitiesTable.tenantId eq tenantId.value.toString()) and
                    (ExternalIdentitiesTable.userId eq userId.value.toString())
            }
            .map { it.toIdentity() }
    }

    override suspend fun insert(tenantId: TenantId, identity: ExternalIdentity): ExternalIdentity = tx {
        ExternalIdentitiesTable.insert { row ->
            row[id] = identity.id
            row[ExternalIdentitiesTable.tenantId] = tenantId.value.toString()
            row[userId] = identity.userId.value.toString()
            row[provider] = identity.provider
            row[subject] = identity.subject
            row[email] = identity.email
            row[createdAt] = identity.createdAt.toEpochMilliseconds()
        }
        identity
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toIdentity(): ExternalIdentity = ExternalIdentity(
        id = this[ExternalIdentitiesTable.id],
        userId = UserId.parse(this[ExternalIdentitiesTable.userId]),
        provider = this[ExternalIdentitiesTable.provider],
        subject = this[ExternalIdentitiesTable.subject],
        email = this[ExternalIdentitiesTable.email],
        createdAt = Instant.fromEpochMilliseconds(this[ExternalIdentitiesTable.createdAt]),
    )
}
