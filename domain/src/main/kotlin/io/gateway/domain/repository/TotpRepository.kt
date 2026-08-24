package io.gateway.domain.repository

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.TotpEnrollment
import io.gateway.domain.model.UserId
import kotlinx.datetime.Instant

/** Persistence port for TOTP enrollments (one per user). Tenant-scoped. */
interface TotpRepository {
    suspend fun find(tenantId: TenantId, userId: UserId): TotpEnrollment?

    suspend fun upsert(tenantId: TenantId, enrollment: TotpEnrollment)

    suspend fun confirm(tenantId: TenantId, userId: UserId, confirmedAt: Instant)

    suspend fun delete(tenantId: TenantId, userId: UserId)
}
