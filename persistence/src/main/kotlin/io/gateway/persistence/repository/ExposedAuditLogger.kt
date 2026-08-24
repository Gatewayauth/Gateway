package io.gateway.persistence.repository

import io.gateway.audit.AuditEventType
import io.gateway.audit.AuditLogger
import io.gateway.audit.AuditQuery
import io.gateway.audit.AuditRecord
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.time.Clock
import io.gateway.persistence.tables.AuditLogTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import java.util.UUID

/** Exposed-backed [AuditLogger] (write) + [AuditQuery] (read) over `audit_log`. Tenant-scoped. */
class ExposedAuditLogger(private val clock: Clock) : AuditLogger, AuditQuery {

    override suspend fun record(
        tenantId: TenantId,
        type: AuditEventType,
        actor: UserId?,
        ip: String?,
        userAgent: String?,
        detail: String?,
        actorLabel: String?,
    ) {
        newSuspendedTransaction(Dispatchers.IO) {
            AuditLogTable.insert { row ->
                row[id] = UUID.randomUUID().toString()
                row[AuditLogTable.tenantId] = tenantId.value.toString()
                row[at] = clock.now().toEpochMilliseconds()
                row[actorUserId] = actor?.value?.toString()
                row[AuditLogTable.actorLabel] = actorLabel
                row[eventType] = type.name
                row[AuditLogTable.ip] = ip
                row[AuditLogTable.userAgent] = userAgent
                row[AuditLogTable.detail] = detail
            }
        }
    }

    override suspend fun recent(tenantId: TenantId, limit: Int): List<AuditRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            AuditLogTable.selectAll()
                .where { AuditLogTable.tenantId eq tenantId.value.toString() }
                .orderBy(AuditLogTable.at to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    AuditRecord(
                        id = row[AuditLogTable.id],
                        at = Instant.fromEpochMilliseconds(row[AuditLogTable.at]),
                        actorUserId = row[AuditLogTable.actorUserId],
                        actorLabel = row[AuditLogTable.actorLabel],
                        eventType = row[AuditLogTable.eventType],
                        ip = row[AuditLogTable.ip],
                        userAgent = row[AuditLogTable.userAgent],
                        detail = row[AuditLogTable.detail],
                    )
                }
        }
}
