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
                    .take(config.maxRows)
                    .mapIndexed { index, page -> rowEntity(config, page, index) }
                dao.replaceRows(appWidgetId, rows)
                NotionResult.Success(Unit)
            }
            is NotionResult.Failure -> result
        }
    }
}
