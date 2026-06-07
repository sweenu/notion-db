package com.notiondb.widgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.notiondb.widgets.App
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget.
 *
 * Phase 0 is a placeholder that reflects connection state so the end-to-end
 * Glance plumbing is proven. Phase 1 replaces [WidgetContent] with a
 * `LazyColumn` of Notion rows (title + Status/checkbox) sourced from the Room
 * cache, refreshed by WorkManager. Phase 2 adds tappable checkbox/Status
 * actions that dispatch to a write-back worker.
 */
class NotionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as App).container
        val connected = container.authProvider.isAuthenticated.first()

        provideContent {
            GlanceTheme {
                WidgetContent(connected)
            }
        }
    }
}

@Composable
private fun WidgetContent(connected: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (connected) {
                "Notion connected — pick a database in the app"
            } else {
                "Open the app to connect Notion"
            },
            style = TextStyle(color = GlanceTheme.colors.onSurface),
        )
    }
}
