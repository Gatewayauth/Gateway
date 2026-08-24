package io.gateway.app

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalAuthTest {

    @Test
    fun startForUnconfiguredProviderReturnsNotFound() = testApplication {
        gatewayTest()
        val client = createClient { followRedirects = false }
        // No external credentials configured in tests, so no provider is enabled.
        val resp = client.get("/t/default/api/auth/external/google/start")
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }
}
