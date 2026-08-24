package io.gateway.audit

import io.gateway.domain.model.TenantId

/** Read side of the audit log for the admin view. */
interface AuditQuery {
    /** Most recent audit entries for a tenant, newest first, capped at [limit]. */
    suspend fun recent(tenantId: TenantId, limit: Int): List<AuditRecord>
}
