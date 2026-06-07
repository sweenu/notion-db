package com.notiondb.widgets.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Binds [NotionWidget] to the AppWidget framework (see AndroidManifest). */
class NotionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotionWidget()
}
