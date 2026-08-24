package io.gateway.audit

/** Security-relevant events written to the append-only audit log. */
enum class AuditEventType {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGOUT,
    ACCOUNT_REGISTERED,
    PASSWORD_CHANGED,
    MFA_ENROLLED,
    MFA_CHALLENGE_FAILED,
    TOKEN_ISSUED,
    TOKEN_REVOKED,
    SIGNING_KEY_ROTATED,
    CLIENT_CREATED,
    CLIENT_DELETED,
    EXTERNAL_IDENTITY_LINKED,
    USER_STATUS_CHANGED,
    SESSIONS_REVOKED,
}
