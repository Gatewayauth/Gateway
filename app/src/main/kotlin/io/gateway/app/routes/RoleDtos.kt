package io.gateway.app.routes

import io.gateway.domain.model.RbacRole
import kotlinx.serialization.Serializable

/** Request/response payloads for the custom RBAC role admin API. */
object RoleDtos {

    @Serializable
    data class RoleResponse(
        val id: String,
        val slug: String,
        val name: String,
        val description: String?,
        val permissions: List<String>,
        val createdAt: Long,
    ) {
        companion object {
            fun of(role: RbacRole): RoleResponse = RoleResponse(
                id = role.id.toString(),
                slug = role.slug,
                name = role.name,
                description = role.description,
                permissions = role.permissions.sorted(),
                createdAt = role.createdAt.toEpochMilliseconds(),
            )
        }
    }

    @Serializable
    data class CreateRoleRequest(
        val slug: String,
        val name: String,
        val description: String? = null,
        val permissions: List<String> = emptyList(),
    )

    /** Full replace of the editable fields (slug is immutable). */
    @Serializable
    data class UpdateRoleRequest(
        val name: String,
        val description: String? = null,
        val permissions: List<String> = emptyList(),
    )

    @Serializable
    data class SetUserRolesRequest(val roleIds: List<String>)
}
