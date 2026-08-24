package io.gateway.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Single-use OIDC authorization codes; PK is the SHA-256 hash of the code. */
object AuthorizationCodesTable : Table("authorization_codes") {
    val codeHash = varchar("code_hash", length = 64)
    val tenantId = varchar("tenant_id", length = 36)
    val clientId = varchar("client_id", length = 200)
    val userId = varchar("user_id", length = 36)
    val redirectUri = text("redirect_uri")
    val scopes = text("scopes")
    val nonce = varchar("nonce", length = 255).nullable()
    val codeChallenge = varchar("code_challenge", length = 255).nullable()
    val codeChallengeMethod = varchar("code_challenge_method", length = 10).nullable()
    val authTime = long("auth_time")
    val expiresAt = long("expires_at")
    val consumedAt = long("consumed_at").nullable()

    override val primaryKey = PrimaryKey(codeHash)
}
