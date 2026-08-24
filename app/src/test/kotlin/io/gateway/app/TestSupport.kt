package io.gateway.app

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Configures a test Gateway instance with an isolated in-memory H2 database. */
fun ApplicationTestBuilder.gatewayTest(adminToken: String = "test-admin-token") {
    val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
    environment {
        config = MapApplicationConfig(
            "gateway.issuer" to "http://localhost:8080",
            "gateway.db.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "gateway.db.driver" to "org.h2.Driver",
            "gateway.db.user" to "sa",
            "gateway.db.password" to "",
            "gateway.db.maxPoolSize" to "4",
            "gateway.session.ttlHours" to "12",
            "gateway.session.cookieName" to "gw_session",
            "gateway.session.cookieSecure" to "false",
            "gateway.admin.token" to adminToken,
            "gateway.security.encKey" to "test-enc-key",
        )
    }
    application { module() }
}

/** Computes the S256 PKCE challenge for a verifier. */
fun pkceChallenge(verifier: String): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
