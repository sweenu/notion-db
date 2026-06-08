package com.notiondb.widgets.widget

import android.app.Application
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the widget body via Glance's unit-test harness (no emulator/launcher)
 * and asserts on the resulting node tree. This is the deterministic stand-in
 * for "place the widget and look at it". Runs under Robolectric because the
 * widget's action callbacks build real android.os.Bundle-backed parameters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class WidgetRenderTest {

    private val config = WidgetConfig(
        appWidgetId = 1,
        databaseId = "db",
        dataSourceId = "ds",
        databaseTitle = "Rule of Life - Check-ins",
        viewId = "v",
        viewName = "All",
        titleProperty = "Name",
        statusProperty = "Status",
    )

    private val rows = listOf(
        WidgetRow("p1", "Morning prayer", null, null, "Not done", "red", emptyList()),
        WidgetRow("p2", "Bible reading", null, null, "Done", "green", emptyList()),
    )

    @Test
    fun connected_widget_renders_rows_and_status() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetRoot(appWidgetId = 1, connected = true, config = config, rows = rows)
        }
        onNode(hasText("Morning prayer")).assertExists()
        onNode(hasText("Bible reading")).assertExists()
        onNode(hasText("Not done")).assertExists()
    }

    @Test
    fun disconnected_widget_shows_connect_prompt() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetRoot(appWidgetId = 1, connected = false, config = null, rows = emptyList())
        }
        onNode(hasText("Open the app to connect Notion")).assertExists()
    }

    @Test
    fun connected_but_unconfigured_prompts_to_configure() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetRoot(appWidgetId = 1, connected = true, config = null, rows = emptyList())
        }
        onNode(hasText("Tap to configure this widget")).assertExists()
    }
}
