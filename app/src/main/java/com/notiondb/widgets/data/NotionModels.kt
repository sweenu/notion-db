package com.notiondb.widgets.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain models for Notion. These are clean Kotlin types the rest of the app
 * uses; the messy mapping from Notion's JSON lives in [NotionJson] so the wire
 * format (which shifts between API versions) is isolated in one place.
 *
 * API context (version 2026-04-01):
 *  - A *database* is a container that holds one or more *data sources*
 *    (the 2025-09-03 change). Rows are queried per data source, not per
 *    database: POST /v1/data_sources/{id}/query.
 *  - A *view* belongs to a database and carries saved filters/sorts that we can
 *    read and query through (the 2026-04-01 Views API).
 */

@Serializable
data class NotionUser(
    val id: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val type: String? = null,
    val bot: BotInfo? = null,
)

@Serializable
data class BotInfo(
    @SerialName("workspace_name") val workspaceName: String? = null,
)

@Serializable
data class NotionError(
    val status: Int = 0,
    val code: String = "",
    val message: String = "",
)

/** A database the integration can see, with its data sources. */
data class NotionDatabase(
    val id: String,
    val title: String,
    val dataSources: List<NotionDataSource>,
)

data class NotionDataSource(
    val id: String,
    val name: String,
)

/** A saved view on a database (table, board, calendar, …). */
data class NotionView(
    val id: String,
    val name: String,
    val type: String,
)

/** The schema of one property as declared on a data source. */
data class PropertySchema(
    val id: String,
    val name: String,
    val type: PropertyType,
    /** Ordered options for select/status properties; empty otherwise. */
    val options: List<PropertyOption> = emptyList(),
    /** Status groups (To-do / In progress / Complete) keyed to option ids. */
    val statusGroups: List<StatusGroup> = emptyList(),
)

data class PropertyOption(val id: String, val name: String, val color: String)

data class StatusGroup(val name: String, val optionIds: List<String>)

enum class PropertyType {
    TITLE, RICH_TEXT, CHECKBOX, STATUS, SELECT, MULTI_SELECT, NUMBER, DATE,
    URL, EMAIL, PHONE, PEOPLE, FORMULA, ROLLUP, CREATED_TIME, LAST_EDITED_TIME,
    RELATION, FILES, BUTTON, UNSUPPORTED;

    companion object {
        fun fromApi(raw: String): PropertyType = when (raw) {
            "title" -> TITLE
            "rich_text" -> RICH_TEXT
            "checkbox" -> CHECKBOX
            "status" -> STATUS
            "select" -> SELECT
            "multi_select" -> MULTI_SELECT
            "number" -> NUMBER
            "date" -> DATE
            "url" -> URL
            "email" -> EMAIL
            "phone_number" -> PHONE
            "people" -> PEOPLE
            "formula" -> FORMULA
            "rollup" -> ROLLUP
            "created_time" -> CREATED_TIME
            "last_edited_time" -> LAST_EDITED_TIME
            "relation" -> RELATION
            "files" -> FILES
            "button" -> BUTTON
            else -> UNSUPPORTED
        }
    }
}

/** A row (page) and its property values, keyed by property name. */
data class NotionPage(
    val id: String,
    val url: String?,
    val properties: Map<String, PropertyValue>,
)

/** A resolved property value, ready to render. */
sealed interface PropertyValue {
    /** Short human-readable form used by the widget's compact list. */
    fun displayText(): String

    data class Text(val value: String) : PropertyValue {
        override fun displayText() = value
    }

    data class Checkbox(val checked: Boolean) : PropertyValue {
        override fun displayText() = if (checked) "✓" else "✗"
    }

    data class Status(val name: String?, val color: String?, val optionId: String?) : PropertyValue {
        override fun displayText() = name.orEmpty()
    }

    data class Select(val name: String?, val color: String?) : PropertyValue {
        override fun displayText() = name.orEmpty()
    }

    data class MultiSelect(val names: List<String>) : PropertyValue {
        override fun displayText() = names.joinToString(", ")
    }

    data class Number(val value: Double?) : PropertyValue {
        override fun displayText() = value?.let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        }.orEmpty()
    }

    data class DateValue(val start: String?, val end: String?) : PropertyValue {
        override fun displayText() = listOfNotNull(start, end).joinToString(" → ")
    }

    data class People(val names: List<String>) : PropertyValue {
        override fun displayText() = names.joinToString(", ")
    }

    data class Plain(val value: String) : PropertyValue {
        override fun displayText() = value
    }

    data class Unsupported(val type: String) : PropertyValue {
        override fun displayText() = ""
    }
}
