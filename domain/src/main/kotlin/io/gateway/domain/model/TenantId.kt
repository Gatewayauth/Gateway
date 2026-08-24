package io.gateway.domain.model

import java.util.UUID

/** Strongly-typed tenant identifier (stable UUID; the human-facing slug can change). */
@JvmInline
value class TenantId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): TenantId = TenantId(UUID.randomUUID())
        fun parse(s: String): TenantId = TenantId(UUID.fromString(s))

        /** The tenant that owns all data created before multi-tenancy (seeded by migration). */
        val DEFAULT: TenantId = TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        const val DEFAULT_SLUG: String = "default"
    }
}
