package com.notiondb.widgets.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persisted widget configuration, keyed by Android's appWidgetId. */
@Entity(tableName = "widget_config")
data class WidgetConfigEntity(
    @PrimaryKey val appWidgetId: Int,
    val databaseId: String,
    val dataSourceId: String,
    val databaseTitle: String,
    val viewId: String?,
    val viewName: String?,
    val titleProperty: String,
    val displayFieldsJson: String,
    val checkboxProperty: String?,
    val statusProperty: String?,
    val themeJson: String,
    val actionsJson: String,
    val maxRows: Int,
)

/**
 * A cached row for a widget. We persist the already-resolved display values so
 * the widget can render instantly from disk without hitting Notion, and so the
 * launcher process never needs network. [pendingWrite] marks an optimistic
 * local change awaiting confirmation from the write-back queue (Phase 2).
 */
@Entity(tableName = "cached_row", primaryKeys = ["appWidgetId", "pageId"])
data class CachedRowEntity(
    val appWidgetId: Int,
    val pageId: String,
    val position: Int,
    val title: String,
    val url: String?,
    val checked: Boolean?,
    val statusName: String?,
    val statusColor: String?,
    val fieldsJson: String,
    val pendingWrite: Boolean = false,
)
