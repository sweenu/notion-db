package com.notiondb.widgets.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notiondb.widgets.App
import com.notiondb.widgets.data.NotionResult
import com.notiondb.widgets.widget.NotionWidget

/**
 * Refreshes one widget (when given [KEY_WIDGET_ID]) or all configured widgets,
 * then asks Glance to re-render. Retryable Notion failures (429 / network /
 * 5xx) bubble up as [Result.retry] so WorkManager applies its backoff; fatal
 * ones (bad token) are swallowed to avoid a hot retry loop.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).container.repository
        val targetId = inputData.getInt(KEY_WIDGET_ID, -1)
        val ids = if (targetId != -1) listOf(targetId) else repo.allConfigIds()

        var shouldRetry = false
        for (id in ids) {
            when (val result = repo.refresh(id)) {
                is NotionResult.Success -> Unit
                is NotionResult.Failure -> if (result.retryable) shouldRetry = true
            }
        }

        // Re-render placed widgets from the freshly-cached rows.
        NotionWidget().updateAll(applicationContext)

        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
    }
}
