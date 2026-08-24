package io.gateway.adminapi

/**
 * Route constants for the admin management API (consumed by the Nuxt admin UI).
 * Handlers land here in a later milestone; centralizing the paths keeps the
 * frontend and backend contracts aligned.
 */
object AdminApiPaths {
    const val BASE = "/api/admin"
    const val CLIENTS = "$BASE/clients"
    const val USERS = "$BASE/users"
    const val SESSIONS = "$BASE/sessions"
    const val PROVIDERS = "$BASE/providers"
    const val AUDIT = "$BASE/audit"
}
