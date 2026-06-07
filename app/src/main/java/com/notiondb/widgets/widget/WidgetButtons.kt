package com.notiondb.widgets.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.notiondb.widgets.model.ButtonAction
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import com.notiondb.widgets.model.WidgetTheme

/** AddRow is the only widget-level action; everything else operates on a row. */
val ButtonAction.isRowScoped: Boolean get() = this !is ButtonAction.AddRow

/** Row of pill buttons for the row-scoped actions configured on this widget. */
@Composable
fun RowActionsBar(config: WidgetConfig, row: WidgetRow, theme: WidgetTheme) {
    Row {
        config.actions.forEachIndexed { index, action ->
            if (!action.isRowScoped) return@forEachIndexed
            val onClick = if (action is ButtonAction.OpenPage) {
                RowActions.openPage(config.appWidgetId, row)
            } else {
                actionRunCallback<ButtonActionCallback>(
                    actionParametersOf(
                        WidgetActionKeys.WIDGET_ID to config.appWidgetId,
                        WidgetActionKeys.PAGE_ID to row.pageId,
                        WidgetActionKeys.ACTION_INDEX to index,
                    ),
                )
            }
            Pill(action.label, theme, onClick)
        }
    }
}

/** Header "+"-style buttons for widget-level actions (AddRow). */
@Composable
fun HeaderActions(config: WidgetConfig, theme: WidgetTheme) {
    Row {
        config.actions.forEachIndexed { index, action ->
            if (action !is ButtonAction.AddRow) return@forEachIndexed
            Pill(
                label = action.label.ifBlank { "+ Add" },
                theme = theme,
                onClick = actionRunCallback<ButtonActionCallback>(
                    actionParametersOf(
                        WidgetActionKeys.WIDGET_ID to config.appWidgetId,
                        WidgetActionKeys.ACTION_INDEX to index,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun Pill(label: String, theme: WidgetTheme, onClick: Action) {
    Text(
        text = label,
        maxLines = 1,
        style = TextStyle(color = ColorProvider(Color(0xFFFFFFFF))),
        modifier = GlanceModifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .cornerRadius(12.dp)
            .background(ColorProvider(Color(theme.accentColor)))
            .clickable(onClick),
    )
}
