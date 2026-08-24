package io.gateway.app

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiTest {

    @Test
    fun swaggerUiIsServed() = testApplication {
        gatewayTest()
        val resp = client.get("/swagger")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("swagger", ignoreCase = true), "should render Swagger UI")
    }

    @Test
    fun openApiSpecIsServed() = testApplication {
        gatewayTest()
        val resp = client.get("/swagger/documentation.yaml")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("openapi:"), "should serve the OpenAPI document")
    }
}
