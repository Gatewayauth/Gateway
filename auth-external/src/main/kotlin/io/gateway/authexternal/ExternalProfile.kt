package io.gateway.authexternal

/**
 * Normalized identity returned by any [IdentityProvider]. [subject] is the
 * provider-unique id; ([id from provider], subject) is the linking key.
 */
data class ExternalProfile(
    val provider: String,
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
)
