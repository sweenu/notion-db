package com.notiondb.widgets.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration

/**
 * Enqueues write-backs. Each (widget, page, op) is a unique work item with
 * REPLACE policy, so rapid taps collapse to the latest desired state instead of
 * queueing a storm of conflicting PATCHes.
 */
object WidgetWriteScheduler {

    private val constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun enqueueCheckbox(context: Context, widgetId: Int, pageId: String, property: String, value: Boolean) {
        enqueue(
            context, widgetId, pageId, WriteBackWorker.OP_CHECKBOX,
            workDataOf(
                WriteBackWorker.KEY_WIDGET_ID to widgetId,
                WriteBackWorker.KEY_PAGE_ID to pageId,
                WriteBackWorker.KEY_OP to WriteBackWorker.OP_CHECKBOX,
                WriteBackWorker.KEY_PROPERTY to property,
                WriteBackWorker.KEY_BOOL to value,
            ),
        )
    }

    fun enqueueStatusAdvance(context: Context, widgetId: Int, pageId: String, property: String) {
        enqueue(
            context, widgetId, pageId, WriteBackWorker.OP_STATUS,
            workDataOf(
                WriteBackWorker.KEY_WIDGET_ID to widgetId,
                WriteBackWorker.KEY_PAGE_ID to pageId,
                WriteBackWorker.KEY_OP to WriteBackWorker.OP_STATUS,
                WriteBackWorker.KEY_PROPERTY to property,
            ),
        )
    }

    private fun enqueue(context: Context, widgetId: Int, pageId: String, op: String, data: Data) {
        val request = OneTimeWorkRequestBuilder<WriteBackWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(5))
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "write_${widgetId}_${pageId}_$op",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
