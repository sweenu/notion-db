package com.notiondb.widgets.model

import kotlinx.serialization.Serializable

/**
 * Everything needed to render one placed widget. Persisted (Room) keyed by the
 * Android `appWidgetId`. Theme (Phase 4) and actions (Phase 3) live here too so
 * the stored schema doesn't churn as later phases land — Phase 1 just leaves
 * them at their defaults / empty.
 */
data class WidgetConfig(
    val appWidgetId: Int,
    val databaseId: String,
    val dataSourceId: String,
    val databaseTitle: String,
    val viewId: String?,
    val viewName: String?,
    val titleProperty: String,
    val displayFields: List<String> = emptyList(),
    val checkboxProperty: String? = null,
    val statusProperty: String? = null,
    /**
     * Render the Status property as a checkbox (like Notion can). Checked means
     * the row's status equals [statusDoneOption]; toggling writes the done /
     * not-done option. Resolved from the Status groups (Complete / To-do) when
     * the user enables it in the builder.
     */
    val statusAsCheckbox: Boolean = false,
    val statusDoneOption: String? = null,
    val statusNotDoneOption: String? = null,
    val theme: WidgetTheme = WidgetTheme(),
    val actions: List<ButtonAction> = emptyList(),
    val maxRows: Int = 25,
    /** Client-side filter layered on top of the view/data source (Phase 4). */
    val hideCheckedRows: Boolean = false,
) {
    /** True when rows are sourced from a saved Notion view rather than the raw DS. */
    val usesView: Boolean get() = viewId != null

    /** True when the Status field should render as an interactive checkbox. */
    val statusIsCheckbox: Boolean
        get() = statusAsCheckbox && statusProperty != null && statusDoneOption != null
}

/** Per-widget appearance (Phase 4). Colors are ARGB packed into a Long. */
@Serializable
data class WidgetTheme(
    val backgroundColor: Long = 0xFF111111,
    val textColor: Long = 0xFFFFFFFF,
    val accentColor: Long = 0xFF2EAADC, // Notion blue-ish
    val cornerRadiusDp: Int = 16,
    val density: Density = Density.COMFORTABLE,
) {
    @Serializable
    enum class Density { COMPACT, COMFORTABLE }
}

/**
 * An app-defined widget action. Because Notion's Button property can't be
 * triggered through the API, these reproduce the useful button behaviours
 * ourselves. The webhook variant is the closest 1:1 to a native Notion button.
 */
@Serializable
sealed interface ButtonAction {
    val label: String

    /** Flip a checkbox property on the row. */
    @Serializable
    data class ToggleCheckbox(override val label: String, val property: String) : ButtonAction

    /** Advance a Status property to the next option (cycles within its groups). */
    @Serializable
    data class AdvanceStatus(override val label: String, val property: String) : ButtonAction

    /** Set a property to a fixed value (select/status/checkbox/text/number). */
    @Serializable
    data class SetProperty(
        override val label: String,
        val property: String,
        val rawValueJson: String,
    ) : ButtonAction

    /** Create a new row, optionally pre-filling properties (JSON object). */
    @Serializable
    data class AddRow(
        override val label: String,
        val presetPropertiesJson: String = "{}",
    ) : ButtonAction

    /** Open the row's Notion page (deep link / web). */
    @Serializable
    data class OpenPage(override val label: String) : ButtonAction

    /** Fire an HTTP POST to a user URL, with the page id in the body. */
    @Serializable
    data class Webhook(
        override val label: String,
        val url: String,
        val includePageId: Boolean = true,
    ) : ButtonAction
}
