package com.notiondb.widgets.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import com.notiondb.widgets.model.WidgetTheme

/**
 * Leading indicator for a row:
 *  - an interactive Glance [CheckBox] when a checkbox property is configured;
 *  - a tappable colored dot (advance Status) when a status property is set;
 *  - otherwise nothing.
 *
 * Both interactions go through [ToggleCheckboxAction] / [AdvanceStatusAction],
 * which update optimistically and enqueue the write-back.
 */
@Composable
fun RowLeading(config: WidgetConfig, row: WidgetRow, theme: WidgetTheme) {
    when {
        // Status shown as a checkbox (the Notion-style toggle).
        config.statusIsCheckbox && row.checked != null ->
            CheckBox(
                checked = row.checked,
                onCheckedChange = actionRunCallback<ToggleStatusCheckboxAction>(
                    rowParams(config.appWidgetId, row.pageId, config.statusProperty ?: ""),
                ),
                text = "",
            )

        config.checkboxProperty != null && row.checked != null ->
            CheckBox(
                checked = row.checked,
                onCheckedChange = actionRunCallback<ToggleCheckboxAction>(
                    rowParams(config.appWidgetId, row.pageId, config.checkboxProperty),
                ),
                text = "",
            )

        config.statusProperty != null ->
            Box(
                modifier = GlanceModifier
                    .size(14.dp)
                    .cornerRadius(7.dp)
                    .background(ColorProvider(dotColor(row, theme)))
                    .clickable(
                        actionRunCallback<AdvanceStatusAction>(
                            rowParams(config.appWidgetId, row.pageId, config.statusProperty),
                        ),
                    ),
            ) {}

        else -> Box(modifier = GlanceModifier.size(0.dp)) {}
    }
}

private fun rowParams(widgetId: Int, pageId: String, property: String) =
    actionParametersOf(
        WidgetActionKeys.WIDGET_ID to widgetId,
        WidgetActionKeys.PAGE_ID to pageId,
        WidgetActionKeys.PROPERTY to property,
    )

/** Dim the dot while a status write is in flight. */
private fun dotColor(row: WidgetRow, theme: WidgetTheme): Color {
    val base = NotionColors.toColor(row.statusColor)
    return if (row.pendingWrite) base.copy(alpha = 0.4f) else base
}

/** Maps Notion's named property colors to ARGB. */
object NotionColors {
    fun toColor(name: String?): Color = when (name) {
        "gray" -> Color(0xFF9B9A97)
        "brown" -> Color(0xFF64473A)
        "orange" -> Color(0xFFD9730D)
        "yellow" -> Color(0xFFDFAB01)
        "green" -> Color(0xFF0F7B6C)
        "blue" -> Color(0xFF0B6E99)
        "purple" -> Color(0xFF6940A5)
        "pink" -> Color(0xFFAD1A72)
        "red" -> Color(0xFFE03E3E)
        else -> Color(0xFF9B9A97)
    }
}
