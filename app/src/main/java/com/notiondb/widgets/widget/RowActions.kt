package com.notiondb.widgets.widget

import android.content.Intent
import android.net.Uri
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.notiondb.widgets.model.WidgetRow
import com.notiondb.widgets.ui.MainActivity

/**
 * Actions attached to widget elements. Phase 1 supports opening a row's Notion
 * page; Phase 2/3 add toggle / status / button callbacks via
 * [androidx.glance.appwidget.action.actionRunCallback].
 */
object RowActions {

    /** Open the row's Notion page; fall back to the app if the row has no URL. */
    fun openPage(appWidgetId: Int, row: WidgetRow): Action {
        val url = row.url
        return if (!url.isNullOrBlank()) {
            actionStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            actionStartActivity<MainActivity>()
        }
    }
}
