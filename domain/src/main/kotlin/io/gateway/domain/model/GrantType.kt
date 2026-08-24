package io.gateway.domain.model

/** OAuth2 / OIDC grant types Gateway supports. */
enum class GrantType(val wireName: String) {
    AUTHORIZATION_CODE("authorization_code"),
    REFRESH_TOKEN("refresh_token"),
    CLIENT_CREDENTIALS("client_credentials"),
    ;

    companion object {
        fun fromWire(value: String): GrantType? = entries.firstOrNull { it.wireName == value }
    }
}
