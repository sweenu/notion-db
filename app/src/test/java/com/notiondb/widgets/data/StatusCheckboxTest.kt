package com.notiondb.widgets.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusCheckboxTest {

    private fun status(
        options: List<Pair<String, String>>, // id to name, in main list order
        groups: List<Pair<String, List<String>>>, // group name to option ids
    ) = PropertySchema(
        id = "s",
        name = "Status",
        type = PropertyType.STATUS,
        options = options.map { PropertyOption(it.first, it.second, "default") },
        statusGroups = groups.map { StatusGroup(it.first, it.second) },
    )

    @Test
    fun `picks the topmost Complete option by options-list order, not group order`() {
        // Real "Rule of Life" case: options listed Not done, Done, Failed;
        // the Complete group's option_ids are [Failed, Done]. Notion checks to
        // the top of the list within Complete -> Done.
        val schema = status(
            options = listOf("n" to "Not done", "d" to "Done", "f" to "Failed"),
            groups = listOf(
                "To-do" to listOf("n"),
                "In progress" to emptyList(),
                "Complete" to listOf("f", "d"),
            ),
        )
        assertEquals("Done" to "Not done", StatusCheckbox.resolveOptions(schema))
    }

    @Test
    fun `simple two-state status resolves done and not-done`() {
        val schema = status(
            options = listOf("n" to "To do", "d" to "Done"),
            groups = listOf("To-do" to listOf("n"), "Complete" to listOf("d")),
        )
        assertEquals("Done" to "To do", StatusCheckbox.resolveOptions(schema))
    }

    @Test
    fun `non-status property yields nulls`() {
        val schema = PropertySchema(id = "x", name = "Name", type = PropertyType.TITLE)
        assertEquals(null to null, StatusCheckbox.resolveOptions(schema))
    }
}
