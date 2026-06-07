package com.notiondb.widgets.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import com.notiondb.widgets.model.WidgetTheme

/**
 * Leading indicator for a row: a checkbox glyph when a checkbox property is
 * configured, otherwise a small colored dot for the Status. Phase 2 swaps the
 * glyph for an interactive Glance `CheckBox` wired to the write-back action.
 */
@Composable
fun RowLeading(config: WidgetConfig, row: WidgetRow, theme: WidgetTheme) {
    val fg = ColorProvider(Color(theme.textColor))
    when {
        config.checkboxProperty != null && row.checked != null ->
            Text(text = if (row.checked) "☑" else "☐", style = TextStyle(color = fg))

        config.statusProperty != null ->
            Box(
                modifier = GlanceModifier
                    .size(10.dp)
                    .cornerRadius(5.dp)
                    .background(ColorProvider(NotionColors.toColor(row.statusColor))),
            ) {}

        else -> Box(modifier = GlanceModifier.size(0.dp)) {}
    }
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
