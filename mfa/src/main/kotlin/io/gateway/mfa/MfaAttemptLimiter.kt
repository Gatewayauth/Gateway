package io.gateway.mfa

import io.gateway.domain.model.UserId

/**
 * Per-user lockout for the MFA second-factor step, bounding TOTP / recovery-code
 * brute force beyond the per-IP rate limit. Implementations may be in-memory
 * (single instance) or shared (Redis) so the lockout holds across instances.
 */
interface MfaAttemptLimiter {
    /** Throw [io.gateway.common.GatewayException.RateLimited] if the user is locked out. */
    suspend fun assertNotLocked(userId: UserId)

    /** Record a failed attempt, locking the account once the threshold is reached. */
    suspend fun recordFailure(userId: UserId)

    /** Clear all state for a user after a successful second factor. */
    suspend fun reset(userId: UserId)
}
