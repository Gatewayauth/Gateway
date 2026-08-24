package io.gateway.persistence.repository

import io.gateway.domain.model.Session
import io.gateway.domain.model.SessionId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.SessionRepository
import io.gateway.persistence.tables.SessionsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/** Exposed-backed [SessionRepository]. Every query is scoped by tenant_id. */
class ExposedSessionRepository : SessionRepository {

    override suspend fun findByTokenHash(tenantId: TenantId, tokenHash: String): Session? = tx {
        SessionsTable.selectAll()
            .where {
                (SessionsTable.tokenHash eq tokenHash) and (SessionsTable.tenantId eq tenantId.value.toString())
            }
            .singleOrNull()?.toSession()
    }

    override suspend fun findById(tenantId: TenantId, id: SessionId): Session? = tx {
        SessionsTable.selectAll()
            .where {
                (SessionsTable.id eq id.value.toString()) and (SessionsTable.tenantId eq tenantId.value.toString())
            }
            .singleOrNull()?.toSession()
    }

    override suspend fun listActiveForUser(tenantId: TenantId, userId: UserId, now: Instant): List<Session> = tx {
        SessionsTable.selectAll()
            .where {
                (SessionsTable.tenantId eq tenantId.value.toString()) and
                    (SessionsTable.userId eq userId.value.toString()) and
                    SessionsTable.revokedAt.isNull() and
                    (SessionsTable.expiresAt greater now.toEpochMilliseconds())
            }
            .orderBy(SessionsTable.lastSeenAt to SortOrder.DESC)
            .map { it.toSession() }
    }

    override suspend fun insert(tenantId: TenantId, session: Session): Session = tx {
        SessionsTable.insert { row ->
            row[id] = session.id.value.toString()
            row[SessionsTable.tenantId] = tenantId.value.toString()
            row[userId] = session.userId.value.toString()
            row[tokenHash] = session.tokenHash
            row[amr] = session.amr.joinToString(" ")
            row[createdAt] = session.createdAt.toEpochMilliseconds()
            row[lastSeenAt] = session.lastSeenAt.toEpochMilliseconds()
            row[expiresAt] = session.expiresAt.toEpochMilliseconds()
            row[revokedAt] = session.revokedAt?.toEpochMilliseconds()
            row[ip] = session.ip
            row[userAgent] = session.userAgent
        }
        session
    }

    override suspend fun touch(tenantId: TenantId, id: SessionId, lastSeenAt: Instant) {
        tx {
            SessionsTable.update({
                (SessionsTable.id eq id.value.toString()) and (SessionsTable.tenantId eq tenantId.value.toString())
            }) { row ->
                row[SessionsTable.lastSeenAt] = lastSeenAt.toEpochMilliseconds()
            }
        }
    }

    override suspend fun revoke(tenantId: TenantId, id: SessionId, revokedAt: Instant) {
        tx {
            SessionsTable.update({
                (SessionsTable.id eq id.value.toString()) and (SessionsTable.tenantId eq tenantId.value.toString())
            }) { row ->
                row[SessionsTable.revokedAt] = revokedAt.toEpochMilliseconds()
            }
        }
    }

    override suspend fun revokeAllForUser(tenantId: TenantId, userId: UserId, revokedAt: Instant) {
        tx {
            SessionsTable.update({
                (SessionsTable.tenantId eq tenantId.value.toString()) and
                    (SessionsTable.userId eq userId.value.toString()) and
                    SessionsTable.revokedAt.isNull()
            }) { row ->
                row[SessionsTable.revokedAt] = revokedAt.toEpochMilliseconds()
            }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toSession(): Session = Session(
        id = SessionId(UUID.fromString(this[SessionsTable.id])),
        userId = UserId(UUID.fromString(this[SessionsTable.userId])),
        tokenHash = this[SessionsTable.tokenHash],
        amr = this[SessionsTable.amr].split(" ").filter { it.isNotBlank() }.toSet(),
        createdAt = Instant.fromEpochMilliseconds(this[SessionsTable.createdAt]),
        lastSeenAt = Instant.fromEpochMilliseconds(this[SessionsTable.lastSeenAt]),
        expiresAt = Instant.fromEpochMilliseconds(this[SessionsTable.expiresAt]),
        revokedAt = this[SessionsTable.revokedAt]?.let { Instant.fromEpochMilliseconds(it) },
        ip = this[SessionsTable.ip],
        userAgent = this[SessionsTable.userAgent],
    )
}
