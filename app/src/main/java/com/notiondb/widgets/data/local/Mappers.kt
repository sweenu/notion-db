package com.notiondb.widgets.data.local

import com.notiondb.widgets.data.NotionPage
import com.notiondb.widgets.data.PropertyValue
import com.notiondb.widgets.model.ButtonAction
import com.notiondb.widgets.model.RowField
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import com.notiondb.widgets.model.WidgetTheme
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Centralised entity ⇄ domain conversions. JSON columns are encoded here. */
object Mappers {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val stringListSerializer = ListSerializer(String.serializer())
    private val actionListSerializer = ListSerializer(ButtonAction.serializer())
    private val fieldListSerializer = ListSerializer(RowField.serializer())

    // --- WidgetConfig -------------------------------------------------------

    fun WidgetConfig.toEntity() = WidgetConfigEntity(
        appWidgetId = appWidgetId,
        databaseId = databaseId,
        dataSourceId = dataSourceId,
        databaseTitle = databaseTitle,
        viewId = viewId,
        viewName = viewName,
        titleProperty = titleProperty,
        displayFieldsJson = json.encodeToString(stringListSerializer, displayFields),
        checkboxProperty = checkboxProperty,
        statusProperty = statusProperty,
        themeJson = json.encodeToString(WidgetTheme.serializer(), theme),
        actionsJson = json.encodeToString(actionListSerializer, actions),
        maxRows = maxRows,
    )

    fun WidgetConfigEntity.toModel() = WidgetConfig(
        appWidgetId = appWidgetId,
        databaseId = databaseId,
        dataSourceId = dataSourceId,
        databaseTitle = databaseTitle,
        viewId = viewId,
        viewName = viewName,
        titleProperty = titleProperty,
        displayFields = decode(displayFieldsJson, stringListSerializer, emptyList()),
        checkboxProperty = checkboxProperty,
        statusProperty = statusProperty,
        theme = decode(themeJson, WidgetTheme.serializer(), WidgetTheme()),
        actions = decode(actionsJson, actionListSerializer, emptyList()),
        maxRows = maxRows,
    )

    // --- rows ---------------------------------------------------------------

    /** Builds a cached row from a freshly-fetched Notion page for [config]. */
    fun rowEntity(config: WidgetConfig, page: NotionPage, position: Int): CachedRowEntity {
        val title = (page.properties[config.titleProperty]?.displayText()).orEmpty()
            .ifBlank { "Untitled" }
        val checked = config.checkboxProperty
            ?.let { page.properties[it] as? PropertyValue.Checkbox }?.checked
        val status = config.statusProperty
            ?.let { page.properties[it] as? PropertyValue.Status }
        val fields = config.displayFields.map { name ->
            RowField(name, page.properties[name]?.displayText().orEmpty())
        }
        return CachedRowEntity(
            appWidgetId = config.appWidgetId,
            pageId = page.id,
            position = position,
            title = title,
            url = page.url,
            checked = checked,
            statusName = status?.name,
            statusColor = status?.color,
            fieldsJson = json.encodeToString(fieldListSerializer, fields),
            pendingWrite = false,
        )
    }

    fun CachedRowEntity.toRow() = WidgetRow(
        pageId = pageId,
        title = title,
        url = url,
        checked = checked,
        statusName = statusName,
        statusColor = statusColor,
        fields = decode(fieldsJson, fieldListSerializer, emptyList()),
        pendingWrite = pendingWrite,
    )

    private fun <T> decode(
        raw: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        fallback: T,
    ): T = runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(fallback)
}
