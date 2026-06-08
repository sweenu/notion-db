package com.notiondb.widgets.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.notiondb.widgets.App
import com.notiondb.widgets.work.WidgetWriteScheduler

/**
 * Refreshes this widget on demand. Widgets can't be "pulled" to refresh, so the
 * header/empty-state are tappable and route here, enqueueing an expedited
 * refresh for this widget's id.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        // Refresh inline (the callback can do network) so the tap is immediate,
        // rather than waiting on a WorkManager job to be scheduled.
        (context.applicationContext as App).container.repository.refresh(widgetId)
        NotionWidget().update(context, glanceId)
    }
}

/** Action parameter keys shared by the widget interactions. */
object WidgetActionKeys {
    val WIDGET_ID = ActionParameters.Key<Int>("widget_id")
    val PAGE_ID = ActionParameters.Key<String>("page_id")
    val PROPERTY = ActionParameters.Key<String>("property")
    val ACTION_INDEX = ActionParameters.Key<Int>("action_index")
}

/**
 * Runs an app-defined button action (Phase 3) by enqueueing [
 * com.notiondb.widgets.work.ButtonActionWorker]. OpenPage is dispatched at
 * render time and never routes here.
 */
class ButtonActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetActionKeys.WIDGET_ID] ?: return
        val actionIndex = parameters[WidgetActionKeys.ACTION_INDEX] ?: return
        val pageId = parameters[WidgetActionKeys.PAGE_ID]
        WidgetWriteScheduler.enqueueButtonAction(context, widgetId, actionIndex, pageId)
    }
}

/**
 * Toggles a checkbox: flips the cache optimistically, re-renders this widget
 * instantly, then enqueues the PATCH. The write-back worker confirms or rolls
 * back.
 */
class ToggleCheckboxAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetActionKeys.WIDGET_ID] ?: return
        val pageId = parameters[WidgetActionKeys.PAGE_ID] ?: return
        val property = parameters[WidgetActionKeys.PROPERTY] ?: return

        val repo = (context.applicationContext as App).container.repository
        val newValue = repo.optimisticToggleCheckbox(widgetId, pageId) ?: return
        NotionWidget().update(context, glanceId)
        WidgetWriteScheduler.enqueueCheckbox(context, widgetId, pageId, property, newValue)
    }
}

/**
 * Toggles a Status rendered as a checkbox: flips checked, sets the status to the
 * configured done / not-done option optimistically, then enqueues the PATCH.
 */
class ToggleStatusCheckboxAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetActionKeys.WIDGET_ID] ?: return
        val pageId = parameters[WidgetActionKeys.PAGE_ID] ?: return

        val container = (context.applicationContext as App).container
        val config = container.repository.getConfig(widgetId) ?: return
        val property = config.statusProperty ?: return
        val done = config.statusDoneOption ?: return
        val notDone = config.statusNotDoneOption ?: done

        val target = container.repository
            .optimisticToggleStatusCheckbox(widgetId, pageId, done, notDone) ?: return
        NotionWidget().update(context, glanceId)
        WidgetWriteScheduler.enqueueStatusSet(context, widgetId, pageId, property, target)
    }
}

/**
 * Advances a Status to its next option. We can't know the next option without
 * the schema, so we just mark the row pending here and let the worker fetch the
 * options, compute the next value, PATCH, and update the cache.
 */
class AdvanceStatusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetActionKeys.WIDGET_ID] ?: return
        val pageId = parameters[WidgetActionKeys.PAGE_ID] ?: return
        val property = parameters[WidgetActionKeys.PROPERTY] ?: return

        val repo = (context.applicationContext as App).container.repository
        repo.markPending(widgetId, pageId, true)
        NotionWidget().update(context, glanceId)
        WidgetWriteScheduler.enqueueStatusAdvance(context, widgetId, pageId, property)
    }
}
