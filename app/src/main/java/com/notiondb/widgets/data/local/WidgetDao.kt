package com.notiondb.widgets.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetDao {

    // --- config -------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: WidgetConfigEntity)

    @Query("SELECT * FROM widget_config WHERE appWidgetId = :id")
    suspend fun getConfig(id: Int): WidgetConfigEntity?

    @Query("SELECT * FROM widget_config WHERE appWidgetId = :id")
    fun observeConfig(id: Int): Flow<WidgetConfigEntity?>

    @Query("SELECT appWidgetId FROM widget_config")
    suspend fun allConfigIds(): List<Int>

    @Query("DELETE FROM widget_config WHERE appWidgetId = :id")
    suspend fun deleteConfig(id: Int)

    // --- rows ---------------------------------------------------------------

    @Query("SELECT * FROM cached_row WHERE appWidgetId = :id ORDER BY position ASC")
    suspend fun getRows(id: Int): List<CachedRowEntity>

    @Query("SELECT * FROM cached_row WHERE appWidgetId = :id AND pageId = :pageId")
    suspend fun getRow(id: Int, pageId: String): CachedRowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(rows: List<CachedRowEntity>)

    @Query("DELETE FROM cached_row WHERE appWidgetId = :id")
    suspend fun clearRows(id: Int)

    @Query("DELETE FROM cached_row WHERE appWidgetId = :id AND pageId = :pageId")
    suspend fun deleteRow(id: Int, pageId: String)

    /** Atomically swap the cached rows for a widget after a refresh. */
    @Transaction
    suspend fun replaceRows(id: Int, rows: List<CachedRowEntity>) {
        clearRows(id)
        insertRows(rows)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRow(row: CachedRowEntity)
}
