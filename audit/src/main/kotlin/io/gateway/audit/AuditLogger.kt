package io.gateway.audit

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId

/**
 * Append-only security audit sink. Implementations persist to the `audit_log`
 * table (or an external SIEM). Never used for application control flow — only
 * for forensics and compliance.
 */
interface AuditLogger {
    suspend fun record(
        tenantId: TenantId,
        type: AuditEventType,
        actor: UserId?,
        ip: String?,
        userAgent: String?,
        detail: String? = null,
        actorLabel: String? = null,
    )
}
