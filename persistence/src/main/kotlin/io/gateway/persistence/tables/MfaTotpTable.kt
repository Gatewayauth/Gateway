package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** TOTP enrollments; the shared secret is stored encrypted (AES-GCM). */
object MfaTotpTable : Table("mfa_totp") {
    val userId = varchar("user_id", length = 36)
    val tenantId = varchar("tenant_id", length = 36)
    val secretEnc = text("secret_enc")
    val confirmedAt = long("confirmed_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(userId)
}
