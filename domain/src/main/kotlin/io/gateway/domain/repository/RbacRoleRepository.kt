package io.gateway.domain.repository

import io.gateway.domain.model.RbacRole
import io.gateway.domain.model.RoleId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId

/** Storage for custom RBAC roles and their assignment to users. All queries are tenant-scoped. */
interface RbacRoleRepository {
    suspend fun list(tenantId: TenantId): List<RbacRole>
    suspend fun findById(tenantId: TenantId, id: RoleId): RbacRole?
    suspend fun findBySlug(tenantId: TenantId, slug: String): RbacRole?
    suspend fun insert(role: RbacRole): RbacRole
    suspend fun update(role: RbacRole): RbacRole
    suspend fun delete(tenantId: TenantId, id: RoleId)

    /** Roles currently assigned to a user. */
    suspend fun listForUser(tenantId: TenantId, userId: UserId): List<RbacRole>

    /** Replaces a user's role assignments with exactly [roleIds]. */
    suspend fun setUserRoles(tenantId: TenantId, userId: UserId, roleIds: Set<RoleId>)
}
