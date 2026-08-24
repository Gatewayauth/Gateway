package io.gateway.mfa

import io.gateway.common.GatewayException
import io.gateway.common.SecretCipher
import io.gateway.common.Sha256
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.TotpEnrollment
import io.gateway.domain.model.User
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.RecoveryCodeRepository
import io.gateway.domain.repository.TotpRepository
import io.gateway.domain.repository.UserRepository
import io.gateway.domain.time.Clock
import kotlinx.datetime.Instant

/**
 * Orchestrates TOTP enrollment and second-factor verification. Secrets are stored
 * encrypted; recovery codes are stored hashed and single-use.
 */
class MfaEnrollmentService(
    private val totp: TotpService,
    private val totpRepo: TotpRepository,
    private val recoveryCodes: RecoveryCodeRepository,
    private val users: UserRepository,
    private val cipher: SecretCipher,
    private val clock: Clock,
) {
    /** Generate a secret and persist an unconfirmed enrollment. */
    suspend fun beginSetup(tenantId: TenantId, user: User): TotpSetup {
        val secret = totp.generateSecret()
        totpRepo.upsert(
            tenantId,
            TotpEnrollment(
                userId = user.id,
                secretEnc = cipher.encrypt(secret),
                confirmedAt = null,
                createdAt = clock.now(),
            ),
        )
        return TotpSetup(secret, totp.provisioningUri(secret, user.email))
    }

    /** Verify the first code, activate MFA, and return one-time recovery codes. */
    suspend fun confirm(tenantId: TenantId, userId: UserId, code: String): List<String> {
        val enrollment = totpRepo.find(tenantId, userId)
            ?: throw GatewayException.Validation("No pending TOTP enrollment.")
        val secret = cipher.decrypt(enrollment.secretEnc)
        if (!totp.verify(secret, code)) throw GatewayException.Validation("Invalid TOTP code.")

        val now = clock.now()
        totpRepo.confirm(tenantId, userId, now)
        val generated = RecoveryCodeGenerator.generate()
        recoveryCodes.replaceAll(tenantId, userId, generated.map { it.hash }, now)
        setMfaRequired(tenantId, userId, required = true, now = now)
        return generated.map { it.plaintext }
    }

    /** Turn off MFA: drop the TOTP enrollment and recovery codes. */
    suspend fun disable(tenantId: TenantId, userId: UserId) {
        val now = clock.now()
        totpRepo.delete(tenantId, userId)
        recoveryCodes.deleteAll(tenantId, userId)
        setMfaRequired(tenantId, userId, required = false, now = now)
    }

    suspend fun isEnrolled(tenantId: TenantId, userId: UserId): Boolean =
        totpRepo.find(tenantId, userId)?.confirmed == true

    /** Keep the denormalized `mfaRequired` flag on the user in sync with enrollment. */
    private suspend fun setMfaRequired(tenantId: TenantId, userId: UserId, required: Boolean, now: Instant) {
        val user = users.findById(tenantId, userId) ?: return
        if (user.mfaRequired != required) users.update(tenantId, user.copy(mfaRequired = required, updatedAt = now))
    }

    /** True if the code is a valid TOTP code OR consumes an unused recovery code. */
    suspend fun verifySecondFactor(tenantId: TenantId, userId: UserId, code: String): Boolean {
        val enrollment = totpRepo.find(tenantId, userId)?.takeIf { it.confirmed } ?: return false
        val secret = cipher.decrypt(enrollment.secretEnc)
        if (totp.verify(secret, code)) return true
        return recoveryCodes.consume(tenantId, userId, Sha256.hashToBase64Url(code), clock.now())
    }
}
