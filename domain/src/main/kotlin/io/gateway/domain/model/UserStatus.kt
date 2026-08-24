package io.gateway.domain.model

/** Account lifecycle state. Only [ACTIVE] users may authenticate. */
enum class UserStatus {
    ACTIVE,
    DISABLED,
    LOCKED,
    PENDING_VERIFICATION,
}
