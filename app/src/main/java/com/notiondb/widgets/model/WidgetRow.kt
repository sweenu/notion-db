package com.notiondb.widgets.model

import kotlinx.serialization.Serializable

/** A single rendered row in a widget. */
data class WidgetRow(
    val pageId: String,
    val title: String,
    val url: String?,
    val checked: Boolean?,
    val statusName: String?,
    val statusColor: String?,
    val fields: List<RowField>,
    val pendingWrite: Boolean = false,
)

@Serializable
data class RowField(val name: String, val value: String)
