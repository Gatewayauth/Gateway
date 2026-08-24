package io.gateway.audit

import kotlinx.datetime.Instant

/** A stored audit-log entry, as read back for the admin audit view. */
data class AuditRecord(
    val id: String,
    val at: Instant,
    val actorUserId: String?,
    val actorLabel: String?,
    val eventType: String,
    val ip: String?,
    val userAgent: String?,
    val detail: String?,
)
