package com.notiondb.widgets.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the property-value JSON shapes that write-back sends. The Status shape
 * was verified live against Notion (HTTP 200), so these assertions match the
 * accepted format.
 */
class PropertyPatchTest {

    @Test
    fun `status patch matches the Notion accepted shape`() {
        // {"Status":{"status":{"name":"Done"}}}
        val patch = PropertyPatch.status("Status", "Done")
        val status = patch["Status"]!!.jsonObject["status"]!!.jsonObject
        assertEquals("Done", status["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `checkbox patch carries a real boolean`() {
        val patch = PropertyPatch.checkbox("Done", true)
        val value = patch["Done"]!!.jsonObject["checkbox"] as JsonPrimitive
        assertTrue(value.boolean)
    }

    @Test
    fun `select patch nests name under select`() {
        val patch = PropertyPatch.select("Priority", "High")
        assertEquals(
            "High",
            patch["Priority"]!!.jsonObject["select"]!!.jsonObject["name"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `title patch builds a rich-text array`() {
        val patch = PropertyPatch.title("Name", "Hello")
        val titleArray = patch["Name"]!!.jsonObject["title"]!!
        // round-trips through the serializer without throwing and contains the text
        val text = Json.encodeToString(JsonObject.serializer(), patch)
        assertTrue(text.contains("\"content\":\"Hello\""))
        assertTrue(titleArray.toString().contains("text"))
    }
}
