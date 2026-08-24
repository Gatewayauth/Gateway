package io.gateway.authlocal

import io.gateway.domain.model.User

/**
 * Result of a registration attempt. [created] is false when the email was already
 * taken: [user] is then a transient, non-persisted placeholder so the API can
 * respond identically to a real signup and not leak account existence. Callers
 * MUST only run post-signup side effects (verification email, audit) when [created].
 */
data class RegistrationOutcome(
    val user: User,
    val created: Boolean,
)
