package io.gateway.app.tenant

import io.gateway.app.plugins.configureStatusPages
import io.gateway.domain.model.Tenant
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.TenantStatus
import io.gateway.domain.repository.TenantRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TenantResolutionTest {

    private val now = Instant.fromEpochMilliseconds(0)

    private val repo = object : TenantRepository {
        override suspend fun findBySlug(slug: String): Tenant? = when (slug) {
            "default" -> Tenant(TenantId.DEFAULT, "default", "Default", TenantStatus.ACTIVE, now)
            "suspended" -> Tenant(TenantId.random(), "suspended", "Susp", TenantStatus.SUSPENDED, now)
            else -> null
        }
        override suspend fun findById(id: TenantId): Tenant? = null
        override suspend fun insert(tenant: Tenant): Tenant = tenant
        override suspend fun list(): List<Tenant> = emptyList()
    }

    private fun setup(builder: io.ktor.server.testing.ApplicationTestBuilder) = builder.application {
        install(ContentNegotiation) { json() }
        configureStatusPages()
        routing {
            tenantScoped {
                get("/ping") {
                    val tenant = call.resolveTenant(repo)
                    call.respondText(tenant.slug)
                }
            }
        }
    }

    @Test
    fun resolvesKnownActiveTenant() = testApplication {
        setup(this)
        val res = client.get("/t/default/ping")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("default", res.bodyAsText())
    }

    @Test
    fun unknownTenantIs404() = testApplication {
        setup(this)
        val res = client.get("/t/nope/ping")
        assertEquals(HttpStatusCode.NotFound, res.status, "body=${res.bodyAsText()}")
    }

    @Test
    fun suspendedTenantIs403() = testApplication {
        setup(this)
        val res = client.get("/t/suspended/ping")
        assertEquals(HttpStatusCode.Forbidden, res.status, "body=${res.bodyAsText()}")
    }
}
