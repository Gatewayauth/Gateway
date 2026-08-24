package io.gateway.mfa

import io.gateway.common.Base64Url
import io.gateway.common.Hmac
import io.gateway.domain.model.UserId
import io.gateway.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Issues and verifies short-lived, HMAC-signed MFA challenge tokens. After a
 * successful password check the user holds one of these until they present the
 * second factor. Stateless (no DB row): `BASE64URL(payload).BASE64URL(mac)`.
 */
class MfaChallengeService(
    private val hmac: Hmac,
    private val clock: Clock,
    private val ttl: Duration = DEFAULT_TTL,
) {
    fun issue(userId: UserId): String {
        val payload = "$userId.${clock.now().plus(ttl).toEpochMilliseconds()}"
        return Base64Url.encode(payload.toByteArray()) + "." + hmac.signToBase64Url(payload)
    }

    /** Returns the user id if the token is authentic and unexpired, else null. */
    fun verify(token: String): UserId? {
        val parts = token.split(".")
        if (parts.size != 2) return null
        val payload = runCatching { String(Base64Url.decode(parts[0])) }.getOrNull() ?: return null
        if (!hmac.verify(payload, parts[1])) return null

        val segments = payload.split(".")
        if (segments.size != 2) return null
        val expiresAt = segments[1].toLongOrNull() ?: return null
        if (clock.now().toEpochMilliseconds() >= expiresAt) return null
        return runCatching { UserId.parse(segments[0]) }.getOrNull()
    }

    private companion object {
        val DEFAULT_TTL = 5.minutes
    }
}
