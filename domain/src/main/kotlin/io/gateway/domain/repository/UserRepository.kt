package io.gateway.domain.repository

import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId

/** Persistence port for [User] aggregates. All operations are scoped to a tenant. */
interface UserRepository {
    suspend fun findById(tenantId: TenantId, id: UserId): User?

    suspend fun findByEmail(tenantId: TenantId, email: String): User?

    suspend fun insert(tenantId: TenantId, user: User): User

    suspend fun update(tenantId: TenantId, user: User): User

    /** Newest-first page of users for the admin view. */
    suspend fun list(tenantId: TenantId, limit: Int, offset: Long): List<User>
}
