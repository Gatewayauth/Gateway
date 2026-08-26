package io.gateway.domain.model

import java.util.UUID

/** Identifier for a custom RBAC [RbacRole]. Wraps a UUID; distinct from other id types. */
@JvmInline
value class RoleId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): RoleId = RoleId(UUID.randomUUID())
        fun parse(s: String): RoleId = RoleId(UUID.fromString(s))
    }
}
