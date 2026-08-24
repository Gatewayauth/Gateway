package io.gateway.authlocal

import io.gateway.common.GatewayException
import io.gateway.common.RandomTokens
import io.gateway.common.Sha256
import io.gateway.domain.model.AccountToken
import io.gateway.domain.model.AccountTokenPurpose
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.model.UserStatus
import io.gateway.domain.notification.Mailer
import io.gateway.domain.repository.AccountTokenRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Issues and consumes email-verification tokens. The raw token is emailed once
 * (only its hash is stored); verifying it flips the account to verified/active.
 */
class EmailVerificationService(
    private val users: UserRepository,
    private val tokens: AccountTokenRepository,
    private val mailer: Mailer,
    private val clock: Clock,
    private val linkBaseUrl: String,
    private val ttl: Duration = DEFAULT_TTL,
) {
    suspend fun startVerification(tenantId: TenantId, user: User) {
        if (user.emailVerified) return
        tokens.deleteForUser(tenantId, user.id, AccountTokenPurpose.EMAIL_VERIFY)
        val raw = RandomTokens.urlSafe()
        tokens.insert(
            tenantId,
            AccountToken(
                tokenHash = Sha256.hashToBase64Url(raw),
                userId = user.id,
                purpose = AccountTokenPurpose.EMAIL_VERIFY,
                expiresAt = clock.now().plus(ttl),
                consumedAt = null,
            ),
        )
        mailer.send(
            to = user.email,
            subject = "Verify your email",
            body = "Confirm your account: $linkBaseUrl/verify-email?token=$raw",
        )
    }

    suspend fun verify(tenantId: TenantId, rawToken: String): User {
        val token = tokens.consume(
            tenantId,
            Sha256.hashToBase64Url(rawToken),
            AccountTokenPurpose.EMAIL_VERIFY,
            clock.now(),
        ) ?: throw GatewayException.Validation("Invalid or expired verification token.")
        val user = users.findById(tenantId, token.userId)
            ?: throw GatewayException.NotFound("User no longer exists.")
        if (user.emailVerified) return user

        val status = if (user.status == UserStatus.PENDING_VERIFICATION) UserStatus.ACTIVE else user.status
        return users.update(tenantId, user.copy(emailVerified = true, status = status, updatedAt = clock.now()))
    }

    private companion object {
        val DEFAULT_TTL = 24.hours
    }
}
