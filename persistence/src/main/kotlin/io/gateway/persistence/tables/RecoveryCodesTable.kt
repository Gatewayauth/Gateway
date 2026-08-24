package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** One-time MFA recovery codes (hashed, single-use). */
object RecoveryCodesTable : Table("recovery_codes") {
    val id = varchar("id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val userId = varchar("user_id", length = 36)
    val codeHash = varchar("code_hash", length = 64)
    val usedAt = long("used_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
