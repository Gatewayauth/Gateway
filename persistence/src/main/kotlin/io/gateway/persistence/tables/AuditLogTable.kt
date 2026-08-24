package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Append-only security audit log. */
object AuditLogTable : Table("audit_log") {
    val id = varchar("id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val at = long("event_at")
    val actorUserId = varchar("actor_user_id", length = 36).nullable()

    // Human-readable actor when there is no user id (e.g. token-authed admin actions).
    val actorLabel = varchar("actor_label", length = 64).nullable()
    val eventType = varchar("event_type", length = 64)
    val ip = varchar("ip", length = 64).nullable()
    val userAgent = varchar("user_agent", length = 512).nullable()
    val detail = text("detail").nullable()

    override val primaryKey = PrimaryKey(id)
}
