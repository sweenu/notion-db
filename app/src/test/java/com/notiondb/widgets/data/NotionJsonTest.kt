package com.notiondb.widgets.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests pinned to the real Notion API shapes (version 2026-03-11) that
 * were captured live during emulator testing. These guard the exact regressions
 * we hit: the `data_source` search shape and the view detail shape.
 */
class NotionJsonTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun obj(s: String): JsonObject = json.parseToJsonElement(s) as JsonObject

    @Test
    fun `search results are parsed as data sources with parent database id`() {
        val response = obj(
            """
            {"results":[
              {"object":"data_source","id":"ds-1","name":"Rule of Life - Check-ins",
               "parent":{"type":"database_id","database_id":"db-1"}}
            ]}
            """.trimIndent(),
        )
        val dbs = NotionJson.parseDataSources(response)
        assertEquals(1, dbs.size)
        assertEquals("db-1", dbs[0].id)
        assertEquals("Rule of Life - Check-ins", dbs[0].title)
        assertEquals("ds-1", dbs[0].dataSources.single().id)
    }

    @Test
    fun `view list returns ids only`() {
        val response = obj("""{"results":[{"object":"view","id":"v-1"},{"object":"view","id":"v-2"}]}""")
        assertEquals(listOf("v-1", "v-2"), NotionJson.parseViewIds(response))
    }

    @Test
    fun `view detail exposes name type data source and sorts`() {
        val view = obj(
            """
            {"object":"view","id":"v-1","data_source_id":"ds-1","name":"Today","type":"list",
             "filter":null,"sorts":[{"property":"jQ;X","direction":"ascending"}]}
            """.trimIndent(),
        )
        val detail = NotionJson.parseViewDetail(view)
        assertEquals("Today", detail.name)
        assertEquals("list", detail.type)
        assertEquals("ds-1", detail.dataSourceId)
        assertNull(detail.filter)
        assertEquals(1, detail.sorts.size)
    }

    @Test
    fun `schema parses title and status with options`() {
        val ds = obj(
            """
            {"properties":{
              "Name":{"id":"title","type":"title","title":{}},
              "Status":{"id":"abc","type":"status","status":{
                "options":[{"id":"o1","name":"Not done","color":"red"},
                           {"id":"o2","name":"Done","color":"green"}],
                "groups":[]}}
            }}
            """.trimIndent(),
        )
        val schema = NotionJson.parseSchema(ds)
        val title = schema.first { it.name == "Name" }
        val status = schema.first { it.name == "Status" }
        assertEquals(PropertyType.TITLE, title.type)
        assertEquals(PropertyType.STATUS, status.type)
        assertEquals(listOf("Not done", "Done"), status.options.map { it.name })
    }

    @Test
    fun `pages parse title and status values`() {
        val response = obj(
            """
            {"results":[
              {"id":"p-1","url":"https://notion.so/p-1","properties":{
                "Name":{"type":"title","title":[{"plain_text":"Morning prayer"}]},
                "Status":{"type":"status","status":{"id":"o1","name":"Not done","color":"red"}}
              }}
            ],"has_more":false}
            """.trimIndent(),
        )
        val result = NotionJson.parsePages(response)
        assertNull(result.nextCursor)
        val page = result.pages.single()
        assertEquals("Morning prayer", (page.properties["Name"] as PropertyValue.Text).value)
        val status = page.properties["Status"] as PropertyValue.Status
        assertEquals("Not done", status.name)
        assertEquals("red", status.color)
    }

    @Test
    fun `has_more surfaces the next cursor`() {
        val response = obj("""{"results":[],"has_more":true,"next_cursor":"cur-123"}""")
        assertEquals("cur-123", NotionJson.parsePages(response).nextCursor)
    }

    @Test
    fun `null status value degrades gracefully`() {
        val response = obj(
            """{"results":[{"id":"p","properties":{"Status":{"type":"status","status":null}}}]}""",
        )
        val status = NotionJson.parsePages(response).pages.single()
            .properties["Status"] as PropertyValue.Status
        assertNull(status.name)
        assertTrue(status.displayText().isEmpty())
    }
}
