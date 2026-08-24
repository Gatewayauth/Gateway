package io.gateway.authexternal

/** Lookup of enabled external identity providers by id. */
class ProviderRegistry(private val providers: Map<String, IdentityProvider>) {
    fun get(id: String): IdentityProvider? = providers[id]

    fun ids(): Set<String> = providers.keys
}
