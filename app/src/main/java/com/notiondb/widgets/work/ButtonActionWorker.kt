package com.notiondb.widgets.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notiondb.widgets.App
import com.notiondb.widgets.data.NotionResult
import com.notiondb.widgets.data.PropertyPatch
import com.notiondb.widgets.data.WebhookSender
import com.notiondb.widgets.model.ButtonAction
import com.notiondb.widgets.widget.NotionWidget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Executes an app-defined widget button (Phase 3). Buttons reproduce the useful
 * behaviours of a native Notion Button property (which the API can't trigger):
 * set a property, add a row, or fire a webhook. OpenPage is handled at render
 * time via an activity intent, so it never reaches this worker.
 */
class ButtonActionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        val repo = container.repository
        val client = container.notionClient

        val widgetId = inputData.getInt(KEY_WIDGET_ID, -1)
        val actionIndex = inputData.getInt(KEY_ACTION_INDEX, -1)
        val pageId = inputData.getString(KEY_PAGE_ID)
        val config = repo.getConfig(widgetId) ?: return Result.success()
        val action = config.actions.getOrNull(actionIndex) ?: return Result.success()

        val result: NotionResult<Unit> = when (action) {
            is ButtonAction.SetProperty -> {
                if (pageId == null) return Result.success()
                val value = parse(action.rawValueJson)
                client.updatePage(pageId, buildJsonObject { put(action.property, value) })
            }

            is ButtonAction.AddRow ->
                client.createPage(config.dataSourceId, parse(action.presetPropertiesJson))

            is ButtonAction.Webhook -> {
                val body = buildJsonObject {
                    if (action.includePageId && pageId != null) put("pageId", pageId)
                    put("widgetId", widgetId)
                }
                if (WebhookSender.post(action.url, body.toString())) NotionResult.Success(Unit)
                else NotionResult.Failure("Webhook failed", retryable = true)
            }

            is ButtonAction.ToggleCheckbox -> {
                if (pageId == null) return Result.success()
                val current = repo.getRow(widgetId, pageId)?.checked ?: false
                client.updatePage(pageId, PropertyPatch.checkbox(action.property, !current))
            }

            is ButtonAction.AdvanceStatus -> {
                // Defer to the dedicated status path for option lookup + cycling.
                WidgetWriteScheduler.enqueueStatusAdvance(applicationContext, widgetId, pageId ?: return Result.success(), action.property)
                NotionResult.Success(Unit)
            }

            is ButtonAction.OpenPage -> NotionResult.Success(Unit) // handled at render
        }

        return when (result) {
            is NotionResult.Success -> {
                // Many actions change server data; resync so the widget reflects it.
                repo.refresh(widgetId)
                NotionWidget().updateAll(applicationContext)
                Result.success()
            }
            is NotionResult.Failure -> if (result.retryable) Result.retry() else Result.failure()
        }
    }

    private fun parse(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as JsonObject }.getOrDefault(JsonObject(emptyMap()))

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_ACTION_INDEX = "action_index"
        const val KEY_PAGE_ID = "page_id"
    }
}
