package io.gateway.authexternal

/** State issued when starting an external login: [cookieValue] is stored in a cookie. */
data class IssuedState(
    val state: String,
    val codeVerifier: String,
    val cookieValue: String,
)
