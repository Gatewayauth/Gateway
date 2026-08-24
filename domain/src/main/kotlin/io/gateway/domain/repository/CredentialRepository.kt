package io.gateway.domain.repository

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId

/**
 * Stores the Argon2 password hash for a user. Separated from [UserRepository] so
 * credential material never travels with a plain User read.
 */
interface CredentialRepository {
    suspend fun findPasswordHash(tenantId: TenantId, userId: UserId): String?

    suspend fun upsertPasswordHash(tenantId: TenantId, userId: UserId, encodedHash: String)

    suspend fun deletePassword(tenantId: TenantId, userId: UserId)
}
