package com.notiondb.widgets.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notiondb.widgets.data.NotionDatabase
import com.notiondb.widgets.data.NotionDataSource
import com.notiondb.widgets.data.NotionResult
import com.notiondb.widgets.data.NotionView
import com.notiondb.widgets.data.PropertySchema
import com.notiondb.widgets.data.PropertyType
import com.notiondb.widgets.data.StatusCheckbox
import com.notiondb.widgets.data.WidgetRepository
import com.notiondb.widgets.model.ButtonAction
import com.notiondb.widgets.model.WidgetConfig
import com.notiondb.widgets.model.WidgetTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Draft state accumulated as the user steps through the builder. */
data class BuilderDraft(
    val databases: List<NotionDatabase> = emptyList(),
    val database: NotionDatabase? = null,
    val dataSource: NotionDataSource? = null,
    val views: List<NotionView> = emptyList(),
    val selectedView: NotionView? = null,
    val schema: List<PropertySchema> = emptyList(),
    val titleProperty: String? = null,
    val displayFields: Set<String> = emptySet(),
    val checkboxProperty: String? = null,
    val statusProperty: String? = null,
    val statusAsCheckbox: Boolean = false,
    val actions: List<ButtonAction> = emptyList(),
    val theme: WidgetTheme = WidgetTheme(),
    val maxRows: Int = 25,
    val hideCheckedRows: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class WidgetBuilderViewModel(
    private val appWidgetId: Int,
    private val repository: WidgetRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow(BuilderDraft())
    val draft: StateFlow<BuilderDraft> = _draft.asStateFlow()

    init { loadDatabases() }

    fun loadDatabases(query: String = "") = launchLoading {
        when (val r = repository.searchDatabases(query)) {
            is NotionResult.Success -> _draft.update { it.copy(databases = r.value, loading = false) }
            is NotionResult.Failure -> fail(r.reason)
        }
    }

    fun selectDatabase(db: NotionDatabase) {
        // Most databases have exactly one data source; default to the first.
        _draft.update {
            it.copy(database = db, dataSource = db.dataSources.firstOrNull(), error = null)
        }
        loadViews()
    }

    fun selectDataSource(ds: NotionDataSource) = _draft.update { it.copy(dataSource = ds) }

    private fun loadViews() = launchLoading {
        val db = _draft.value.database ?: return@launchLoading fail("Pick a database first")
        when (val r = repository.listViews(db.id)) {
            // Views are optional; a failure here shouldn't block "whole database".
            is NotionResult.Success -> _draft.update { it.copy(views = r.value, loading = false) }
            is NotionResult.Failure -> _draft.update { it.copy(views = emptyList(), loading = false) }
        }
    }

    fun selectView(view: NotionView?) = _draft.update { it.copy(selectedView = view) }

    fun loadSchema() = launchLoading {
        val ds = _draft.value.dataSource ?: return@launchLoading fail("No data source")
        when (val r = repository.getSchema(ds.id)) {
            is NotionResult.Success -> {
                val title = r.value.firstOrNull { it.type == PropertyType.TITLE }?.name
                _draft.update { it.copy(schema = r.value, titleProperty = it.titleProperty ?: title, loading = false) }
            }
            is NotionResult.Failure -> fail(r.reason)
        }
    }

    fun setTitleProperty(name: String) = _draft.update { it.copy(titleProperty = name) }

    fun toggleField(name: String) = _draft.update {
        val next = if (name in it.displayFields) it.displayFields - name else it.displayFields + name
        it.copy(displayFields = next)
    }

    fun setCheckboxProperty(name: String?) = _draft.update { it.copy(checkboxProperty = name) }
    fun setStatusProperty(name: String?) = _draft.update {
        it.copy(statusProperty = name, statusAsCheckbox = if (name == null) false else it.statusAsCheckbox)
    }
    fun setStatusAsCheckbox(enabled: Boolean) = _draft.update { it.copy(statusAsCheckbox = enabled) }

    // --- actions (Phase 3) --------------------------------------------------

    fun addAction(action: ButtonAction) = _draft.update { it.copy(actions = it.actions + action) }

    fun removeAction(index: Int) = _draft.update {
        it.copy(actions = it.actions.filterIndexed { i, _ -> i != index })
    }

    // --- style / filters (Phase 4) ------------------------------------------

    fun setBackgroundColor(argb: Long) = _draft.update { it.copy(theme = it.theme.copy(backgroundColor = argb)) }
    fun setAccentColor(argb: Long) = _draft.update { it.copy(theme = it.theme.copy(accentColor = argb)) }
    fun setTextColor(argb: Long) = _draft.update { it.copy(theme = it.theme.copy(textColor = argb)) }
    fun setDensity(density: WidgetTheme.Density) = _draft.update { it.copy(theme = it.theme.copy(density = density)) }
    fun setMaxRows(count: Int) = _draft.update { it.copy(maxRows = count.coerceIn(1, 100)) }
    fun setHideChecked(hide: Boolean) = _draft.update { it.copy(hideCheckedRows = hide) }

    /** Builds, persists, and triggers a first refresh. Returns the config or null. */
    fun save(onSaved: (WidgetConfig) -> Unit) {
        val d = _draft.value
        val db = d.database
        val ds = d.dataSource
        val title = d.titleProperty
        if (db == null || ds == null || title == null) {
            fail("Choose a database and a title field before saving.")
            return
        }
        val (doneOption, notDoneOption) =
            if (d.statusAsCheckbox && d.statusProperty != null) resolveStatusCheckboxOptions(d)
            else null to null

        val config = WidgetConfig(
            appWidgetId = appWidgetId,
            databaseId = db.id,
            dataSourceId = ds.id,
            databaseTitle = db.title,
            viewId = d.selectedView?.id,
            viewName = d.selectedView?.name,
            titleProperty = title,
            displayFields = d.displayFields.toList(),
            checkboxProperty = d.checkboxProperty,
            statusProperty = d.statusProperty,
            statusAsCheckbox = d.statusAsCheckbox && doneOption != null,
            statusDoneOption = doneOption,
            statusNotDoneOption = notDoneOption,
            actions = d.actions,
            theme = d.theme,
            maxRows = d.maxRows,
            hideCheckedRows = d.hideCheckedRows,
        )
        viewModelScope.launch {
            repository.saveConfig(config)
            onSaved(config)
        }
    }

    private fun resolveStatusCheckboxOptions(d: BuilderDraft): Pair<String?, String?> {
        val status = d.schema.firstOrNull {
            it.name == d.statusProperty && it.type == PropertyType.STATUS
        } ?: return null to null
        return StatusCheckbox.resolveOptions(status)
    }

    private fun fail(message: String) = _draft.update { it.copy(loading = false, error = message) }

    private fun launchLoading(block: suspend () -> Unit) {
        _draft.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { block() }
    }
}
