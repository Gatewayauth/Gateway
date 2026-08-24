package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** JWT signing keys; private key material stored encrypted. */
object SigningKeysTable : Table("signing_keys") {
    val kid = varchar("kid", length = 64)
    val tenantId = varchar("tenant_id", length = 36)
    val algorithm = varchar("algorithm", length = 16)
    val publicJwk = text("public_jwk")
    val privateKeyEnc = text("private_key_enc")
    val active = bool("active")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at").nullable()

    override val primaryKey = PrimaryKey(kid)
}
