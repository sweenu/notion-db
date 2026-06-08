package com.notiondb.widgets.data

/**
 * Resolves which Status options mean "checked" (done) and "unchecked" (not done)
 * when a Status property is shown as a checkbox.
 *
 * Matches Notion's own behaviour: checking sets the status to the **topmost**
 * option of the "Complete" group, where "top" is the option's position in the
 * property's main options list (not the group's internal `option_ids` order,
 * which can differ — e.g. a Complete group of [Failed, Done] by option_ids is
 * still resolved to Done because Done comes first in the options list).
 */
object StatusCheckbox {

    /** Returns (doneOption, notDoneOption) names, or nulls if unresolvable. */
    fun resolveOptions(status: PropertySchema): Pair<String?, String?> {
        if (status.type != PropertyType.STATUS) return null to null
        val nameById = status.options.associate { it.id to it.name }
        val orderIndex = status.options.withIndex().associate { it.value.name to it.index }
        val groups = status.statusGroups

        // The topmost option of a group = the member that appears first in the
        // main options list.
        fun topOf(group: StatusGroup?): String? =
            group?.optionIds?.mapNotNull { nameById[it] }
                ?.minByOrNull { orderIndex[it] ?: Int.MAX_VALUE }

        val completeGroup = groups.firstOrNull { it.name.contains("complet", ignoreCase = true) }
            ?: groups.lastOrNull()
        val todoGroup = groups.firstOrNull {
            it.name.contains("to-do", ignoreCase = true) || it.name.contains("to do", ignoreCase = true)
        } ?: groups.firstOrNull()

        val done = topOf(completeGroup) ?: status.options.lastOrNull()?.name
        val notDone = topOf(todoGroup) ?: status.options.firstOrNull()?.name
        return done to notDone
    }
}
