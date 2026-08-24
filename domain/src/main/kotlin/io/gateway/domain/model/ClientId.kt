package io.gateway.domain.model

/** OAuth2 client identifier. Public value shared with relying parties. */
@JvmInline
value class ClientId(val value: String) {
    override fun toString(): String = value
}
