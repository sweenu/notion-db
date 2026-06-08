package com.notiondb.widgets.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notiondb.widgets.App
import com.notiondb.widgets.data.NotionResult
import com.notiondb.widgets.data.PropertyOption
import com.notiondb.widgets.data.PropertyPatch
import com.notiondb.widgets.data.PropertyType
import com.notiondb.widgets.data.getOrNull
import com.notiondb.widgets.widget.NotionWidget

/**
 * Performs a single write-back to Notion. The cache was already updated
 * optimistically by the tap callback; this worker confirms it.
 *
 *  - Retryable failures (429 / network / 5xx) → [Result.retry] (WorkManager
 *    backoff). The optimistic value stays on screen meanwhile.
 *  - Fatal failures (bad token, validation) → resync the widget from Notion so
 *    the optimistic change is rolled back to the truth, then [Result.failure].
 */
class WriteBackWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        val repo = container.repository
        val client = container.notionClient

        val widgetId = inputData.getInt(KEY_WIDGET_ID, -1)
        val pageId = inputData.getString(KEY_PAGE_ID) ?: return Result.success()
        val op = inputData.getString(KEY_OP) ?: return Result.success()
        val property = inputData.getString(KEY_PROPERTY) ?: return Result.success()
        val config = repo.getConfig(widgetId) ?: return Result.success()

        val result: NotionResult<Unit> = when (op) {
            OP_CHECKBOX -> {
                val value = inputData.getBoolean(KEY_BOOL, false)
                client.updatePage(pageId, PropertyPatch.checkbox(property, value))
            }

            OP_STATUS -> {
                val schema = repo.getSchema(config.dataSourceId).getOrNull()
                    ?: return Result.retry() // couldn't read options; try again later
                val options = schema
                    .firstOrNull { it.name == property && it.type == PropertyType.STATUS }
                    ?.options.orEmpty()
                val current = repo.getRow(widgetId, pageId)?.statusName
                val next = nextStatus(options, current) ?: return Result.success()

                client.updatePage(pageId, PropertyPatch.status(property, next.name)).also {
                    if (it is NotionResult.Success) {
                        repo.applyStatus(widgetId, pageId, next.name, next.color)
                    }
                }
            }

            OP_STATUS_SET -> {
                val value = inputData.getString(KEY_VALUE) ?: return Result.success()
                client.updatePage(pageId, PropertyPatch.status(property, value))
            }

            else -> NotionResult.Success(Unit)
        }

        return when (result) {
            is NotionResult.Success -> {
                repo.clearPending(widgetId, pageId)
                NotionWidget().updateAll(applicationContext)
                Result.success()
            }
            is NotionResult.Failure ->
                if (result.retryable) {
                    Result.retry()
                } else {
                    repo.refresh(widgetId) // roll back optimistic change to server truth
                    NotionWidget().updateAll(applicationContext)
                    Result.failure()
                }
        }
    }

    /** Next option after [current] in declared order, wrapping to the start. */
    private fun nextStatus(options: List<PropertyOption>, current: String?): PropertyOption? {
        if (options.isEmpty()) return null
        val index = options.indexOfFirst { it.name == current }
        return options[(index + 1) % options.size]
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_PAGE_ID = "page_id"
        const val KEY_OP = "op"
        const val KEY_PROPERTY = "property"
        const val KEY_BOOL = "bool"
        const val KEY_VALUE = "value"

        const val OP_CHECKBOX = "checkbox"
        const val OP_STATUS = "status"
        const val OP_STATUS_SET = "status_set"
    }
}
