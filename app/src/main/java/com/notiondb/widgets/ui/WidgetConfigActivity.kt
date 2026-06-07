package com.notiondb.widgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiondb.widgets.App
import com.notiondb.widgets.ui.builder.WidgetBuilderScreen
import com.notiondb.widgets.ui.builder.WidgetBuilderViewModel
import com.notiondb.widgets.ui.theme.NotionDbTheme
import com.notiondb.widgets.widget.NotionWidget
import com.notiondb.widgets.work.WidgetRefreshScheduler
import kotlinx.coroutines.launch

/**
 * Launched by the launcher when a widget is dropped on the home screen
 * (ACTION_APPWIDGET_CONFIGURE). Runs the builder; on save it persists the
 * config, kicks an immediate refresh, ensures the periodic refresh is running,
 * and returns RESULT_OK so the widget is actually placed.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default to CANCELED: if the user backs out, the widget isn't placed.
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = (application as App).container.repository

        setContent {
            NotionDbTheme {
                val vm: WidgetBuilderViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            WidgetBuilderViewModel(appWidgetId, repository) as T
                    },
                )
                WidgetBuilderScreen(vm = vm, onSaved = { finishWithWidget(appWidgetId) })
            }
        }
    }

    private fun finishWithWidget(appWidgetId: Int) {
        lifecycleScope.launch {
            // Render once immediately so the widget isn't blank, then schedule.
            NotionWidget().updateAll(applicationContext)
            WidgetRefreshScheduler.refreshNow(applicationContext, appWidgetId)
            WidgetRefreshScheduler.ensurePeriodic(applicationContext)

            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}
