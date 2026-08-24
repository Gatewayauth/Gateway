package io.gateway.authlocal

import io.gateway.common.GatewayException
import io.gateway.domain.auth.PasswordHasher
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.notification.Mailer
import io.gateway.domain.repository.CredentialRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock

/** Creates local accounts: validates input, hashes the password, persists user + credential. */
class RegistrationService(
    private val users: UserRepository,
    private val credentials: CredentialRepository,
    private val hasher: PasswordHasher,
    private val clock: Clock,
    private val mailer: Mailer,
    private val linkBaseUrl: String,
) {
    /**
     * Registers a local account. When the email already exists this does NOT throw
     * (that would let signup enumerate accounts): it notifies the address and returns
     * a transient, non-persisted [RegistrationOutcome] with `created = false` that
     * mirrors a fresh signup. The password is hashed on both paths so response timing
     * doesn't distinguish them either.
     */
    suspend fun register(
        tenantId: TenantId,
        email: String,
        displayName: String?,
        password: CharArray,
    ): RegistrationOutcome {
        val normalized = email.trim().lowercase()
        if (!normalized.contains('@')) throw GatewayException.Validation("Invalid email.")
        PasswordPolicy.validate(password)

        val now = clock.now()
        val trimmedName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        // Always hash: it equalises timing between the new and existing-email paths and
        // wipes the supplied password array. Discarded when the account already exists.
        val encoded = hasher.hash(password)

        if (users.findByEmail(tenantId, normalized) != null) {
            mailer.send(
                to = normalized,
                subject = "You already have an account",
                body = "Someone tried to register an account with this email, but one already " +
                    "exists. If this was you, sign in instead — or reset your password: $linkBaseUrl/forgot",
            )
            val transient = User(
                id = UserId.random(),
                email = normalized,
                emailVerified = false,
                displayName = trimmedName,
                status = UserStatus.PENDING_VERIFICATION,
                mfaRequired = false,
                createdAt = now,
                updatedAt = now,
            )
            return RegistrationOutcome(user = transient, created = false)
        }

        val user = User(
            id = UserId.random(),
            email = normalized,
            emailVerified = false,
            displayName = trimmedName,
            status = UserStatus.PENDING_VERIFICATION,
            mfaRequired = false,
            createdAt = now,
            updatedAt = now,
        )
        val saved = users.insert(tenantId, user)
        credentials.upsertPasswordHash(tenantId, saved.id, encoded)
        return RegistrationOutcome(user = saved, created = true)
    }
}
