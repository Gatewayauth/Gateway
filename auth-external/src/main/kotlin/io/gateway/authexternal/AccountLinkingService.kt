package io.gateway.authexternal

import io.gateway.common.GatewayException
import io.gateway.domain.model.ExternalIdentity
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.model.UserStatus
import io.gateway.domain.repository.ExternalIdentityRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import java.util.UUID

/**
 * Resolves an external [ExternalProfile] to a local [User], creating or linking as
 * needed. Linking rules are takeover-safe:
 *  - A known (provider, subject) always logs into its linked user.
 *  - An unlinked profile links to an existing local account ONLY if the provider
 *    verified the email. An unverified email that already exists locally is refused.
 *  - Otherwise a new account is created (requires the provider to supply an email).
 */
class AccountLinkingService(
    private val users: UserRepository,
    private val identities: ExternalIdentityRepository,
    private val clock: Clock,
) {
    suspend fun resolve(tenantId: TenantId, profile: ExternalProfile): User {
        identities.findByProviderSubject(tenantId, profile.provider, profile.subject)?.let { existing ->
            return users.findById(tenantId, existing.userId)
                ?: throw GatewayException.NotFound("Linked user no longer exists.")
        }

        // Normalize the same way local auth/registration does, so a mixed-case
        // provider email still matches an existing account (no duplicate users).
        val email = profile.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: throw GatewayException.Validation("The ${profile.provider} account has no email to link.")
        val existingLocal = users.findByEmail(tenantId, email)

        if (existingLocal != null) {
            if (!profile.emailVerified) {
                throw GatewayException.Conflict(
                    "An account with this email exists. Sign in and link ${profile.provider} from settings.",
                )
            }
            link(tenantId, existingLocal.id, profile)
            return existingLocal
        }

        return createLinkedUser(tenantId, profile, email)
    }

    private suspend fun createLinkedUser(tenantId: TenantId, profile: ExternalProfile, email: String): User {
        val now = clock.now()
        val user = users.insert(
            tenantId,
            User(
                id = UserId.random(),
                email = email,
                emailVerified = profile.emailVerified,
                displayName = profile.displayName,
                status = UserStatus.ACTIVE,
                mfaRequired = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        link(tenantId, user.id, profile)
        return user
    }

    private suspend fun link(tenantId: TenantId, userId: UserId, profile: ExternalProfile) {
        identities.insert(
            tenantId,
            ExternalIdentity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                provider = profile.provider,
                subject = profile.subject,
                email = profile.email,
                createdAt = clock.now(),
            ),
        )
    }
}
