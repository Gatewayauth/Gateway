package io.gateway.authlocal

import io.gateway.common.GatewayException
import io.gateway.common.RandomTokens
import io.gateway.common.Sha256
import io.gateway.domain.auth.PasswordHasher
import io.gateway.domain.model.AccountToken
import io.gateway.domain.model.AccountTokenPurpose
import io.gateway.domain.model.TenantId
import io.gateway.domain.notification.Mailer
import io.gateway.domain.repository.AccountTokenRepository
import io.gateway.domain.repository.CredentialRepository
import io.gateway.domain.repository.SessionRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Password-reset flow. [request] never reveals whether an email exists (no
 * enumeration). [reset] validates the new password, replaces the hash, and revokes
 * all of the user's sessions so a compromised session can't survive a reset.
 */
class PasswordResetService(
    private val users: UserRepository,
    private val credentials: CredentialRepository,
    private val tokens: AccountTokenRepository,
    private val sessions: SessionRepository,
    private val hasher: PasswordHasher,
    private val mailer: Mailer,
    private val clock: Clock,
    private val linkBaseUrl: String,
    private val ttl: Duration = DEFAULT_TTL,
) {
    suspend fun request(tenantId: TenantId, email: String) {
        val user = users.findByEmail(tenantId, email.trim().lowercase()) ?: return
        tokens.deleteForUser(tenantId, user.id, AccountTokenPurpose.PASSWORD_RESET)
        val raw = RandomTokens.urlSafe()
        tokens.insert(
            tenantId,
            AccountToken(
                tokenHash = Sha256.hashToBase64Url(raw),
                userId = user.id,
                purpose = AccountTokenPurpose.PASSWORD_RESET,
                expiresAt = clock.now().plus(ttl),
                consumedAt = null,
            ),
        )
        mailer.send(
            to = user.email,
            subject = "Reset your password",
            body = "Reset your password: $linkBaseUrl/reset-password?token=$raw",
        )
    }

    suspend fun reset(tenantId: TenantId, rawToken: String, newPassword: CharArray) {
        val token = tokens.consume(
            tenantId,
            Sha256.hashToBase64Url(rawToken),
            AccountTokenPurpose.PASSWORD_RESET,
            clock.now(),
        ) ?: throw GatewayException.Validation("Invalid or expired reset token.")
        PasswordPolicy.validate(newPassword)
        credentials.upsertPasswordHash(tenantId, token.userId, hasher.hash(newPassword))
        // Revoke existing sessions: a reset must invalidate any active/compromised session.
        sessions.revokeAllForUser(tenantId, token.userId, clock.now())
    }

    private companion object {
        val DEFAULT_TTL = 1.hours
    }
}
