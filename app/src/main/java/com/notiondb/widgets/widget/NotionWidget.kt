package com.notiondb.widgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import com.notiondb.widgets.ui.WidgetConfigActivity

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

        val configure = actionStartActivity(
            Intent(context, WidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        provideContent {
            WidgetRoot(appWidgetId, connected, config, rows, configure)
        }
    }
}

/** Widget body. Exposed (internal) so Glance unit tests can render it directly. */
@Composable
internal fun WidgetRoot(
    appWidgetId: Int,
    connected: Boolean,
    config: WidgetConfig?,
    rows: List<WidgetRow>,
    configure: Action? = null,
) {
    val theme = config?.theme ?: WidgetTheme()
    val bg = ColorProvider(Color(theme.backgroundColor))
    val fg = ColorProvider(Color(theme.textColor))
    val refresh = actionRunCallback<RefreshWidgetAction>()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(theme.cornerRadiusDp.dp)
            .padding(12.dp),
    ) {
        when {
            !connected -> CenteredMessage("Open the app to connect Notion", fg)
            config == null -> CenteredMessage("Tap to configure this widget", fg, configure)
            rows.isEmpty() -> {
                Header(config, theme, fg, refresh, configure)
                CenteredMessage("Loading… tap to refresh", fg, refresh)
            }
            else -> {
                Header(config, theme, fg, refresh, configure)
                Spacer(GlanceModifier.height(8.dp))
                RowList(config, rows, theme, fg)
            }
        }
    }
}

@Composable
private fun Header(
    config: WidgetConfig,
    theme: WidgetTheme,
    fg: ColorProvider,
    refresh: Action,
    configure: Action?,
) {
    val label = config.viewName?.let { "${config.databaseTitle} · $it" } ?: config.databaseTitle
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = label,
            style = TextStyle(color = fg, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        HeaderActions(config, theme)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = "↻",
            style = TextStyle(color = fg, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.clickable(refresh).padding(horizontal = 4.dp),
        )
        if (configure != null) {
            Text(
                text = "⚙",
                style = TextStyle(color = fg, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.clickable(configure).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun RowList(
    config: WidgetConfig,
    rows: List<WidgetRow>,
    theme: WidgetTheme,
    fg: ColorProvider,
) {
    val verticalPad = if (theme.density == WidgetTheme.Density.COMPACT) 3.dp else 7.dp
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows, itemId = { it.pageId.hashCode().toLong() }) { row ->
            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = verticalPad)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(RowActions.openPage(config.appWidgetId, row)),
                ) {
                    RowLeading(config, row, theme)
                    Spacer(GlanceModifier.width(8.dp))
                    if (!row.icon.isNullOrBlank()) {
                        Text(text = row.icon, maxLines = 1, style = TextStyle(color = fg))
                        Spacer(GlanceModifier.width(6.dp))
                    }
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
                    if (config.actions.any { it.isRowScoped }) {
                        Spacer(GlanceModifier.width(8.dp))
                        RowActionsBar(config, row, theme)
                    }
                }
                val subtitle = row.fields
                    .filter { it.value.isNotBlank() }
                    .joinToString("   ·   ") { it.value }
                if (subtitle.isNotBlank() && theme.density != WidgetTheme.Density.COMPACT) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color(theme.textColor).copy(alpha = 0.6f))),
                        modifier = GlanceModifier.padding(start = 22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, fg: ColorProvider, onTap: Action? = null) {
    val base = GlanceModifier.fillMaxSize()
    Box(
        modifier = if (onTap != null) base.clickable(onTap) else base,
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = fg))
    }
}
