package com.notiondb.widgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.notiondb.widgets.App
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import com.notiondb.widgets.model.WidgetTheme

/**
 * The home-screen widget: a vertical list of Notion rows rendered entirely from
 * the Room cache (no network in the launcher process). The data is kept fresh
 * by [com.notiondb.widgets.work.RefreshWorker]; tapping a row opens its Notion
 * page. Checkbox/Status interaction and action buttons are layered on in later
 * phases via [RowLeading] and the action callbacks.
 */
class NotionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as App).container
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val connected = container.authProvider.currentToken() != null
        val config = container.repository.getConfig(appWidgetId)
        val rows = if (config != null) container.repository.getRows(appWidgetId) else emptyList()

        provideContent {
            WidgetRoot(appWidgetId, connected, config, rows)
        }
    }
}

@Composable
private fun WidgetRoot(
    appWidgetId: Int,
    connected: Boolean,
    config: WidgetConfig?,
    rows: List<WidgetRow>,
) {
    val theme = config?.theme ?: WidgetTheme()
    val bg = ColorProvider(Color(theme.backgroundColor))
    val fg = ColorProvider(Color(theme.textColor))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(theme.cornerRadiusDp.dp)
            .padding(12.dp),
    ) {
        when {
            !connected -> CenteredMessage("Open the app to connect Notion", fg)
            config == null -> CenteredMessage("Tap to configure this widget", fg)
            rows.isEmpty() -> {
                Header(config, fg)
                CenteredMessage("No rows yet — pull to refresh", fg)
            }
            else -> {
                Header(config, fg)
                Spacer(GlanceModifier.height(8.dp))
                RowList(config, rows, theme, fg)
            }
        }
    }
}

@Composable
private fun Header(config: WidgetConfig, fg: ColorProvider) {
    val label = config.viewName?.let { "${config.databaseTitle} · $it" } ?: config.databaseTitle
    Text(
        text = label,
        style = TextStyle(color = fg, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

@Composable
private fun RowList(
    config: WidgetConfig,
    rows: List<WidgetRow>,
    theme: WidgetTheme,
    fg: ColorProvider,
) {
    val verticalPad = if (theme.density == WidgetTheme.Density.COMPACT) 4.dp else 8.dp
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows, itemId = { it.pageId.hashCode().toLong() }) { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = verticalPad)
                    .clickable(RowActions.openPage(config.appWidgetId, row)),
            ) {
                RowLeading(config, row, theme)
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = row.title,
                    maxLines = 1,
                    style = TextStyle(color = fg),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (row.statusName != null) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = row.statusName,
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color(theme.accentColor))),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, fg: ColorProvider) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = fg))
    }
}
