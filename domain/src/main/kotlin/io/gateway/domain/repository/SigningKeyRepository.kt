package io.gateway.domain.repository

import io.gateway.domain.model.SigningKeyRecord
import io.gateway.domain.model.TenantId
import kotlinx.datetime.Instant

/** Persistence port for JWT signing keys. Each tenant has its own key set. */
interface SigningKeyRepository {
    suspend fun all(tenantId: TenantId): List<SigningKeyRecord>

    suspend fun insert(tenantId: TenantId, record: SigningKeyRecord)

    /** Mark a key inactive and set the instant after which it may be dropped from JWKS. */
    suspend fun retire(tenantId: TenantId, kid: String, expiresAt: Instant)
}
