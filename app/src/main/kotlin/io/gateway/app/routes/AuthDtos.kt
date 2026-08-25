package io.gateway.app.routes

import io.gateway.domain.model.Session
import io.gateway.domain.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request/response payloads for the local-auth API. Nested to keep one top-level type per file. */
object AuthDtos {

    @Serializable
    data class RegisterRequest(
        val email: String,
        val password: String,
        val displayName: String? = null,
    )

    @Serializable
    data class LoginRequest(
        val email: String,
        val password: String,
    )

    @Serializable
    data class UserResponse(
        val id: String,
        val email: String,
        val emailVerified: Boolean,
        val displayName: String?,
        val status: String,
        val mfaRequired: Boolean,
        val role: String,
        val superAdmin: Boolean,
    ) {
        companion object {
            fun of(user: User): UserResponse = UserResponse(
                id = user.id.toString(),
                email = user.email,
                emailVerified = user.emailVerified,
                displayName = user.displayName,
                status = user.status.name,
                mfaRequired = user.mfaRequired,
                role = user.role.name,
                superAdmin = user.superAdmin,
            )
        }
    }

    @Serializable
    data class MessageResponse(val message: String)

    /** Returned by /login when the account has MFA: exchange the token at /login/mfa. */
    @Serializable
    data class MfaChallengeResponse(
        val mfaRequired: Boolean = true,
        val mfaToken: String,
    )

    @Serializable
    data class MfaLoginRequest(
        val mfaToken: String,
        val code: String,
    )

    @Serializable
    data class VerifyEmailRequest(val token: String)

    @Serializable
    data class ForgotPasswordRequest(val email: String)

    @Serializable
    data class ResetPasswordRequest(val token: String, val password: String)

    @Serializable
    data class SessionSummary(
        val id: String,
        @SerialName("created_at") val createdAt: Long,
        @SerialName("last_seen_at") val lastSeenAt: Long,
        val ip: String?,
        @SerialName("user_agent") val userAgent: String?,
        val current: Boolean,
    ) {
        companion object {
            fun of(session: Session, current: Boolean): SessionSummary = SessionSummary(
                id = session.id.toString(),
                createdAt = session.createdAt.toEpochMilliseconds(),
                lastSeenAt = session.lastSeenAt.toEpochMilliseconds(),
                ip = session.ip,
                userAgent = session.userAgent,
                current = current,
            )
        }
    }
}
