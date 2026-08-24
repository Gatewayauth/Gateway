package io.gateway.persistence

/** Connection settings for the primary Postgres database. Secrets come from env. */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val driverClassName: String = "org.postgresql.Driver",
    val maxPoolSize: Int = 10,
)
