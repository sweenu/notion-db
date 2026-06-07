package com.notiondb.widgets.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration

/**
 * Schedules widget refreshes. WorkManager's periodic floor is 15 minutes, so we
 * run a single coalesced background refresh every 30 minutes (battery-friendly)
 * and offer an expedited one-off refresh for taps / "just placed" moments.
 */
object WidgetRefreshScheduler {

    private const val PERIODIC_WORK = "notion_widget_periodic_refresh"
    private const val ONESHOT_WORK = "notion_widget_oneshot_refresh"

    private val networkConstraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(Duration.ofMinutes(30))
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Refresh now: a specific widget, or all of them when [appWidgetId] is null. */
    fun refreshNow(context: Context, appWidgetId: Int? = null) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .apply {
                appWidgetId?.let {
                    setInputData(workDataOf(RefreshWorker.KEY_WIDGET_ID to it))
                }
            }
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            appWidgetId?.let { "${ONESHOT_WORK}_$it" } ?: ONESHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
