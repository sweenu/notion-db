package com.notiondb.widgets.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the `properties` payloads for PATCH/POST page calls. Centralised so
 * the exact Notion property-value shapes live in one place (write-back in
 * Phase 2, the action engine in Phase 3).
 */
object PropertyPatch {

    fun checkbox(property: String, value: Boolean): JsonObject = buildJsonObject {
        putJsonObject(property) { put("checkbox", value) }
    }

    fun status(property: String, name: String): JsonObject = buildJsonObject {
        putJsonObject(property) { putJsonObject("status") { put("name", name) } }
    }

    fun select(property: String, name: String): JsonObject = buildJsonObject {
        putJsonObject(property) { putJsonObject("select") { put("name", name) } }
    }

    fun number(property: String, value: Double): JsonObject = buildJsonObject {
        putJsonObject(property) { put("number", value) }
    }

    fun title(property: String, text: String): JsonObject = buildJsonObject {
        putJsonObject(property) {
            putJsonArray("title") {
                add(buildJsonObject { putJsonObject("text") { put("content", text) } })
            }
        }
    }

    fun richText(property: String, text: String): JsonObject = buildJsonObject {
        putJsonObject(property) {
            putJsonArray("rich_text") {
                add(buildJsonObject { putJsonObject("text") { put("content", text) } })
            }
        }
    }
}
