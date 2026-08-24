package io.gateway.domain.model

/** Purpose of a single-use account token (scopes what the token may be used for). */
enum class AccountTokenPurpose {
    EMAIL_VERIFY,
    PASSWORD_RESET,
}
