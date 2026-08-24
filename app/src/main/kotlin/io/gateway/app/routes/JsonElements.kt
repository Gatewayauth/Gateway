package io.gateway.app.routes

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts the plain Map/List structures returned by the OIDC layer into
 * kotlinx JsonElements so ContentNegotiation can serialize them without a
 * compile-time serializer for `Any`.
 */
object JsonElements {

    fun of(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to of(v) })
        is Iterable<*> -> JsonArray(value.map { of(it) })
        else -> JsonPrimitive(value.toString())
    }
}
