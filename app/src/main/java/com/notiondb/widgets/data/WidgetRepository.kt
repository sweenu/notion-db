package com.notiondb.widgets.data

import com.notiondb.widgets.data.local.Mappers.toEntity
import com.notiondb.widgets.data.local.Mappers.toModel
import com.notiondb.widgets.data.local.Mappers.toRow
import com.notiondb.widgets.data.local.Mappers.rowEntity
import com.notiondb.widgets.data.local.WidgetDao
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for widget data: it knows how to populate the Room
 * cache from Notion and exposes the cached rows the widget renders. The builder
 * UI and the refresh worker both go through here.
 */
class WidgetRepository(
    private val client: NotionClient,
    private val dao: WidgetDao,
) {
    // --- builder lookups (live Notion calls) --------------------------------

    suspend fun searchDatabases(query: String = "") = client.searchDatabases(query)
    suspend fun getSchema(dataSourceId: String) = client.getSchema(dataSourceId)
    suspend fun listViews(databaseId: String) = client.listViews(databaseId)

    // --- config -------------------------------------------------------------

    suspend fun saveConfig(config: WidgetConfig) = dao.upsertConfig(config.toEntity())

    suspend fun getConfig(appWidgetId: Int): WidgetConfig? =
        dao.getConfig(appWidgetId)?.toModel()

    fun observeConfig(appWidgetId: Int): Flow<WidgetConfig?> =
        dao.observeConfig(appWidgetId).map { it?.toModel() }

    suspend fun allConfigIds(): List<Int> = dao.allConfigIds()

    suspend fun deleteWidget(appWidgetId: Int) {
        dao.deleteConfig(appWidgetId)
        dao.clearRows(appWidgetId)
    }

    // --- rows ----------------------------------------------------------------

    suspend fun getRows(appWidgetId: Int): List<WidgetRow> =
        dao.getRows(appWidgetId).map { it.toRow() }

    suspend fun getRow(appWidgetId: Int, pageId: String): WidgetRow? =
        dao.getRow(appWidgetId, pageId)?.toRow()

    // --- optimistic write-back helpers (Phase 2/3) --------------------------

    /** Flip the cached checkbox immediately; returns the new value (or null). */
    suspend fun optimisticToggleCheckbox(appWidgetId: Int, pageId: String): Boolean? {
        val row = dao.getRow(appWidgetId, pageId) ?: return null
        val next = !(row.checked ?: false)
        dao.upsertRow(row.copy(checked = next, pendingWrite = true))
        return next
    }

    /**
     * Toggle a Status-as-checkbox row: flip checked, set the status to the done
     * or not-done option optimistically, and return the target status name to
     * write back (or null if the row is gone).
     */
    suspend fun optimisticToggleStatusCheckbox(
        appWidgetId: Int,
        pageId: String,
        doneOption: String,
        notDoneOption: String,
    ): String? {
        val row = dao.getRow(appWidgetId, pageId) ?: return null
        val nowChecked = !(row.checked ?: false)
        val target = if (nowChecked) doneOption else notDoneOption
        dao.upsertRow(row.copy(checked = nowChecked, statusName = target, pendingWrite = true))
        return target
    }

    suspend fun markPending(appWidgetId: Int, pageId: String, pending: Boolean) {
        val row = dao.getRow(appWidgetId, pageId) ?: return
        dao.upsertRow(row.copy(pendingWrite = pending))
    }

    suspend fun applyStatus(appWidgetId: Int, pageId: String, name: String?, color: String?) {
        val row = dao.getRow(appWidgetId, pageId) ?: return
        dao.upsertRow(row.copy(statusName = name, statusColor = color, pendingWrite = false))
    }

    suspend fun clearPending(appWidgetId: Int, pageId: String) =
        markPending(appWidgetId, pageId, false)

    /**
     * Pulls the latest rows from Notion (through the saved view when one is
     * configured, otherwise the raw data source) and atomically swaps the cache.
     */
    suspend fun refresh(appWidgetId: Int): NotionResult<Unit> {
        val config = getConfig(appWidgetId)
            ?: return NotionResult.Failure("Widget not configured", retryable = false)

        val result = if (config.usesView) {
            client.queryView(config.viewId!!, pageSize = config.maxRows)
        } else {
            client.queryDataSource(config.dataSourceId, pageSize = config.maxRows)
        }

        return when (result) {
            is NotionResult.Success -> {
                val rows = result.value.pages
                    .map { page -> rowEntity(config, page, 0) }
                    .let { built ->
                        // Client-side filter layered on the view/data source.
                        if (config.hideCheckedRows) built.filter { it.checked != true } else built
                    }
                    .take(config.maxRows)
                    .mapIndexed { index, row -> row.copy(position = index) }
                dao.replaceRows(appWidgetId, rows)
                NotionResult.Success(Unit)
            }
            is NotionResult.Failure -> result
        }
    }
}
