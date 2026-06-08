package com.notiondb.widgets.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Translates Notion's JSON into the domain models. All wire-format knowledge —
 * and therefore all the parts most likely to need adjustment when the API
 * shifts — is concentrated here.
 *
 * NOTE: a few container shapes (search → data_sources, the Views API payloads)
 * are coded to the documented data-source / Views structures. They're defensive (missing
 * fields degrade gracefully) but should be sanity-checked against a live
 * response before relying on them in production.
 */
object NotionJson {

    /**
     * Parses /v1/search results filtered to `data_source`. Each result is a
     * data source (the queryable unit); we surface it as a [NotionDatabase] with
     * a single data source, using the parent database id for view lookups.
     */
    fun parseDataSources(response: JsonObject): List<NotionDatabase> =
        response.array("results").mapNotNull { el ->
            val obj = el.jsonObject
            val dataSourceId = obj.string("id") ?: return@mapNotNull null
            val name = obj.string("name")
                ?: obj.array("title").plainText().ifBlank { null }
                ?: "Untitled"
            val parentDatabaseId =
                obj["parent"]?.jsonObject?.string("database_id") ?: dataSourceId
            NotionDatabase(
                id = parentDatabaseId,
                title = name,
                dataSources = listOf(NotionDataSource(dataSourceId, name)),
            )
        }

    /** The view-list endpoint returns only `{object, id}` per view. */
    fun parseViewIds(response: JsonObject): List<String> =
        response.array("results").mapNotNull { it.jsonObject.string("id") }

    /** Full view detail (GET /v1/views/{id}): name, type, data source, filter, sorts. */
    fun parseViewDetail(obj: JsonObject): NotionViewDetail = NotionViewDetail(
        id = obj.string("id").orEmpty(),
        name = obj.string("name") ?: "Untitled",
        type = obj.string("type") ?: "table",
        dataSourceId = obj.string("data_source_id").orEmpty(),
        filter = obj["filter"] as? JsonObject,
        sorts = obj.array("sorts").mapNotNull { it as? JsonObject },
    )

    /** Reads a data source's `properties` map into ordered schema entries. */
    fun parseSchema(dataSource: JsonObject): List<PropertySchema> {
        val props = dataSource["properties"]?.jsonObject ?: return emptyList()
        return props.entries.map { (name, value) ->
            val po = value.jsonObject
            val type = PropertyType.fromApi(po.string("type") ?: "")
            PropertySchema(
                id = po.string("id") ?: name,
                name = name,
                type = type,
                options = parseOptions(po, type),
                statusGroups = parseStatusGroups(po, type),
            )
        }
    }

    private fun parseOptions(po: JsonObject, type: PropertyType): List<PropertyOption> {
        val container = when (type) {
            PropertyType.SELECT -> po["select"]?.jsonObject
            PropertyType.MULTI_SELECT -> po["multi_select"]?.jsonObject
            PropertyType.STATUS -> po["status"]?.jsonObject
            else -> null
        } ?: return emptyList()
        return container.array("options").mapNotNull {
            val o = it.jsonObject
            val id = o.string("id") ?: return@mapNotNull null
            PropertyOption(id, o.string("name") ?: "", o.string("color") ?: "default")
        }
    }

    private fun parseStatusGroups(po: JsonObject, type: PropertyType): List<StatusGroup> {
        if (type != PropertyType.STATUS) return emptyList()
        val status = po["status"]?.jsonObject ?: return emptyList()
        return status.array("groups").mapNotNull {
            val g = it.jsonObject
            val name = g.string("name") ?: return@mapNotNull null
            val ids = g.array("option_ids").mapNotNull { id -> id.jsonPrimitive.contentOrNull }
            StatusGroup(name, ids)
        }
    }

    data class PageQueryResult(val pages: List<NotionPage>, val nextCursor: String?)

    fun parsePages(response: JsonObject): PageQueryResult {
        val pages = response.array("results").mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj.string("id") ?: return@mapNotNull null
            val props = obj["properties"]?.jsonObject ?: JsonObject(emptyMap())
            NotionPage(
                id = id,
                url = obj.string("url"),
                properties = props.entries.associate { (name, value) ->
                    name to parsePropertyValue(value.jsonObject)
                },
            )
        }
        val cursor = if (response["has_more"]?.jsonPrimitive?.booleanOrNull == true) {
            response.string("next_cursor")
        } else null
        return PageQueryResult(pages, cursor)
    }

    /** Maps one property *value* object from a page into a [PropertyValue]. */
    fun parsePropertyValue(prop: JsonObject): PropertyValue {
        return when (PropertyType.fromApi(prop.string("type") ?: "")) {
            PropertyType.TITLE -> PropertyValue.Text(prop.array("title").plainText())
            PropertyType.RICH_TEXT -> PropertyValue.Text(prop.array("rich_text").plainText())
            PropertyType.CHECKBOX ->
                PropertyValue.Checkbox(prop["checkbox"]?.jsonPrimitive?.booleanOrNull ?: false)
            PropertyType.STATUS -> {
                val s = prop["status"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
                PropertyValue.Status(s?.string("name"), s?.string("color"), s?.string("id"))
            }
            PropertyType.SELECT -> {
                val s = prop["select"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
                PropertyValue.Select(s?.string("name"), s?.string("color"))
            }
            PropertyType.MULTI_SELECT ->
                PropertyValue.MultiSelect(prop.array("multi_select").mapNotNull { it.jsonObject.string("name") })
            PropertyType.NUMBER ->
                PropertyValue.Number(prop["number"]?.jsonPrimitive?.doubleOrNull)
            PropertyType.DATE -> {
                val d = prop["date"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
                PropertyValue.DateValue(d?.string("start"), d?.string("end"))
            }
            PropertyType.URL -> PropertyValue.Plain(prop.string("url").orEmpty())
            PropertyType.EMAIL -> PropertyValue.Plain(prop.string("email").orEmpty())
            PropertyType.PHONE -> PropertyValue.Plain(prop.string("phone_number").orEmpty())
            PropertyType.PEOPLE ->
                PropertyValue.People(prop.array("people").mapNotNull { it.jsonObject.string("name") })
            PropertyType.CREATED_TIME -> PropertyValue.Plain(prop.string("created_time").orEmpty())
            PropertyType.LAST_EDITED_TIME -> PropertyValue.Plain(prop.string("last_edited_time").orEmpty())
            PropertyType.FORMULA -> PropertyValue.Plain(parseFormula(prop["formula"]?.jsonObject))
            PropertyType.ROLLUP -> PropertyValue.Plain(parseRollup(prop["rollup"]?.jsonObject))
            else -> PropertyValue.Unsupported(prop.string("type").orEmpty())
        }
    }

    private fun parseFormula(formula: JsonObject?): String {
        formula ?: return ""
        return when (formula.string("type")) {
            "string" -> formula.string("string").orEmpty()
            "number" -> formula["number"]?.jsonPrimitive?.contentOrNull.orEmpty()
            "boolean" -> if (formula["boolean"]?.jsonPrimitive?.booleanOrNull == true) "✓" else "✗"
            "date" -> formula["date"]?.jsonObject?.string("start").orEmpty()
            else -> ""
        }
    }

    private fun parseRollup(rollup: JsonObject?): String {
        rollup ?: return ""
        return when (rollup.string("type")) {
            "number" -> rollup["number"]?.jsonPrimitive?.contentOrNull.orEmpty()
            "date" -> rollup["date"]?.jsonObject?.string("start").orEmpty()
            "array" -> rollup.array("array").size.let { if (it == 0) "" else "$it items" }
            else -> ""
        }
    }

    // --- small JSON helpers -------------------------------------------------

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())

    /** Concatenates the `plain_text` of a Notion rich-text array. */
    private fun JsonArray.plainText(): String =
        joinToString("") { it.jsonObject.string("plain_text").orEmpty() }
}
