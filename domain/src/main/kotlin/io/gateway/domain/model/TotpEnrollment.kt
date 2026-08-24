package io.gateway.domain.model

import kotlinx.datetime.Instant

/**
 * A user's TOTP MFA enrollment. [secretEnc] is the AES-GCM-encrypted shared secret.
 * [confirmedAt] is null until the user proves possession with a valid code; only a
 * confirmed enrollment gates login.
 */
data class TotpEnrollment(
    val userId: UserId,
    val secretEnc: String,
    val confirmedAt: Instant?,
    val createdAt: Instant,
) {
    val confirmed: Boolean get() = confirmedAt != null
}
