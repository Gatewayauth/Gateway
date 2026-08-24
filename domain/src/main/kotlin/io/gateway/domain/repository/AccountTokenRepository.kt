package io.gateway.domain.repository

import io.gateway.domain.model.AccountToken
import io.gateway.domain.model.AccountTokenPurpose
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import kotlinx.datetime.Instant

/** Persistence port for single-use account tokens. Tenant-scoped. */
interface AccountTokenRepository {
    suspend fun insert(tenantId: TenantId, token: AccountToken)

    /**
     * Atomically consume a token matching [tokenHash] and [purpose] that is unexpired
     * and unused, returning it (marked consumed). Null on any mismatch/expiry/reuse.
     */
    suspend fun consume(
        tenantId: TenantId,
        tokenHash: String,
        purpose: AccountTokenPurpose,
        now: Instant,
    ): AccountToken?

    /** Invalidate any outstanding tokens of a purpose for a user (e.g. on re-request). */
    suspend fun deleteForUser(tenantId: TenantId, userId: UserId, purpose: AccountTokenPurpose)
}
