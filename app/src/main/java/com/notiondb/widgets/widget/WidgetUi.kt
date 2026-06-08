package com.notiondb.widgets.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import com.notiondb.widgets.model.WidgetTheme

/**
 * Leading indicator for a row:
 *  - a tappable checkbox glyph (☑/☐) when a checkbox or Status-as-checkbox is
 *    configured — driven purely by our cached `checked` so it never desyncs
 *    (a real RemoteViews CheckBox keeps its own toggle state in the launcher
 *    and fights our optimistic update);
 *  - a tappable colored dot (advance Status) when a plain status is set;
 *  - otherwise nothing.
 */
@Composable
fun RowLeading(config: WidgetConfig, row: WidgetRow, theme: WidgetTheme) {
    val fg = ColorProvider(Color(theme.textColor))
    val accent = ColorProvider(Color(theme.accentColor))
    when {
        // Status shown as a checkbox (the Notion-style toggle).
        config.statusIsCheckbox && row.checked != null ->
            CheckGlyph(
                checked = row.checked,
                fg = fg,
                accent = accent,
                action = actionRunCallback<ToggleStatusCheckboxAction>(
                    rowParams(config.appWidgetId, row.pageId, config.statusProperty ?: ""),
                ),
            )

        config.checkboxProperty != null && row.checked != null ->
            CheckGlyph(
                checked = row.checked,
                fg = fg,
                accent = accent,
                action = actionRunCallback<ToggleCheckboxAction>(
                    rowParams(config.appWidgetId, row.pageId, config.checkboxProperty),
                ),
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

/** A data-driven checkbox: appearance comes only from [checked], tap fires [action]. */
@Composable
private fun CheckGlyph(checked: Boolean, fg: ColorProvider, accent: ColorProvider, action: Action) {
    Text(
        text = if (checked) "☑" else "☐",
        style = TextStyle(color = if (checked) accent else fg, fontSize = 18.sp),
        modifier = GlanceModifier.padding(end = 2.dp).clickable(action),
    )
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
