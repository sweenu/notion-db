package com.notiondb.widgets.ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notiondb.widgets.data.PropertyType
import com.notiondb.widgets.model.ButtonAction
import com.notiondb.widgets.model.WidgetConfig

/**
 * Three-step builder: pick database → pick view → choose fields. A single
 * [WidgetBuilderViewModel] holds the draft so the steps share state.
 */
@Composable
fun WidgetBuilderScreen(
    vm: WidgetBuilderViewModel,
    onSaved: (WidgetConfig) -> Unit,
) {
    val nav = rememberNavController()
    val draft by vm.draft.collectAsStateWithLifecycle()

    NavHost(
        navController = nav,
        startDestination = "database",
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        composable("database") {
            DatabaseStep(
                draft = draft,
                onSearch = vm::loadDatabases,
                onSelect = {
                    vm.selectDatabase(it)
                    nav.navigate("view")
                },
            )
        }
        composable("view") {
            ViewStep(
                draft = draft,
                onSelectDataSource = vm::selectDataSource,
                onSelectView = vm::selectView,
                onNext = {
                    vm.loadSchema()
                    nav.navigate("fields")
                },
            )
        }
        composable("fields") {
            FieldsStep(
                draft = draft,
                onTitle = vm::setTitleProperty,
                onToggleField = vm::toggleField,
                onCheckbox = vm::setCheckboxProperty,
                onStatus = vm::setStatusProperty,
                onStatusAsCheckbox = vm::setStatusAsCheckbox,
                onNext = { nav.navigate("actions") },
            )
        }
        composable("actions") {
            ActionsStep(
                draft = draft,
                onAdd = vm::addAction,
                onRemove = vm::removeAction,
                onSave = { vm.save(onSaved) },
            )
        }
    }
}

@Composable
private fun DatabaseStep(
    draft: BuilderDraft,
    onSearch: (String) -> Unit,
    onSelect: (com.notiondb.widgets.data.NotionDatabase) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    StepScaffold(title = "Choose a database", draft = draft) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            label = { Text("Search databases") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(draft.databases) { db ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(db) },
                ) {
                    Text(
                        text = db.title,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewStep(
    draft: BuilderDraft,
    onSelectDataSource: (com.notiondb.widgets.data.NotionDataSource) -> Unit,
    onSelectView: (com.notiondb.widgets.data.NotionView?) -> Unit,
    onNext: () -> Unit,
) {
    StepScaffold(title = "Choose a view", draft = draft, action = "Next" to onNext) {
        val db = draft.database
        if (db != null && db.dataSources.size > 1) {
            Text("Data source", style = MaterialTheme.typography.labelLarge)
            db.dataSources.forEach { ds ->
                SelectableRow(
                    text = ds.name,
                    selected = draft.dataSource?.id == ds.id,
                    onClick = { onSelectDataSource(ds) },
                )
            }
        }
        Text("View", style = MaterialTheme.typography.labelLarge)
        SelectableRow(
            text = "Whole database (no view filter)",
            selected = draft.selectedView == null,
            onClick = { onSelectView(null) },
        )
        draft.views.forEach { view ->
            SelectableRow(
                text = "${view.name}  ·  ${view.type}",
                selected = draft.selectedView?.id == view.id,
                onClick = { onSelectView(view) },
            )
        }
    }
}

@Composable
private fun FieldsStep(
    draft: BuilderDraft,
    onTitle: (String) -> Unit,
    onToggleField: (String) -> Unit,
    onCheckbox: (String?) -> Unit,
    onStatus: (String?) -> Unit,
    onStatusAsCheckbox: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    StepScaffold(title = "Choose fields", draft = draft, action = "Next: buttons" to onNext) {
        val checkboxProps = draft.schema.filter { it.type == PropertyType.CHECKBOX }
        val statusProps = draft.schema.filter { it.type == PropertyType.STATUS }

        Text("Title field", style = MaterialTheme.typography.labelLarge)
        draft.schema.filter { it.type == PropertyType.TITLE }.forEach { prop ->
            SelectableRow(prop.name, draft.titleProperty == prop.name) { onTitle(prop.name) }
        }

        Text("Show these fields", style = MaterialTheme.typography.labelLarge)
        draft.schema
            .filter { it.type != PropertyType.TITLE }
            .forEach { prop ->
                FilterChip(
                    selected = prop.name in draft.displayFields,
                    onClick = { onToggleField(prop.name) },
                    label = { Text("${prop.name}  (${prop.type.name.lowercase()})") },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

        if (checkboxProps.isNotEmpty()) {
            Text("Interactive checkbox (optional)", style = MaterialTheme.typography.labelLarge)
            SelectableRow("None", draft.checkboxProperty == null) { onCheckbox(null) }
            checkboxProps.forEach { p ->
                SelectableRow(p.name, draft.checkboxProperty == p.name) { onCheckbox(p.name) }
            }
        }
        if (statusProps.isNotEmpty()) {
            Text("Status field (optional)", style = MaterialTheme.typography.labelLarge)
            SelectableRow("None", draft.statusProperty == null) { onStatus(null) }
            statusProps.forEach { p ->
                SelectableRow(p.name, draft.statusProperty == p.name) { onStatus(p.name) }
            }
            if (draft.statusProperty != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Status as a checkbox", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Checked = Complete; tapping toggles the status.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = draft.statusAsCheckbox, onCheckedChange = onStatusAsCheckbox)
                }
            }
        }
    }
}

@Composable
private fun ActionsStep(
    draft: BuilderDraft,
    onAdd: (ButtonAction) -> Unit,
    onRemove: (Int) -> Unit,
    onSave: () -> Unit,
) {
    var webhookUrl by remember { mutableStateOf("") }
    StepScaffold(title = "Buttons (optional)", draft = draft, action = "Save widget" to onSave) {
        Text(
            "Notion's Button field can't be triggered through the API, so add app " +
                "buttons that perform the same actions.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (draft.actions.isNotEmpty()) {
            Text("Added", style = MaterialTheme.typography.labelLarge)
            draft.actions.forEachIndexed { index, action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${action.label.ifBlank { "(button)" }}  ·  ${actionTypeName(action)}",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                }
            }
        }

        Text("Add a button", style = MaterialTheme.typography.labelLarge)
        Button(
            onClick = { onAdd(ButtonAction.OpenPage("Open")) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) { Text("Open page") }
        Button(
            onClick = { onAdd(ButtonAction.AddRow(label = "+ Add")) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) { Text("Add row") }

        OutlinedTextField(
            value = webhookUrl,
            onValueChange = { webhookUrl = it },
            label = { Text("Webhook URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            enabled = webhookUrl.isNotBlank(),
            onClick = {
                onAdd(ButtonAction.Webhook(label = "Run", url = webhookUrl.trim()))
                webhookUrl = ""
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) { Text("Add webhook button") }
    }
}

private fun actionTypeName(action: ButtonAction): String = when (action) {
    is ButtonAction.OpenPage -> "open page"
    is ButtonAction.AddRow -> "add row"
    is ButtonAction.Webhook -> "webhook"
    is ButtonAction.ToggleCheckbox -> "toggle"
    is ButtonAction.AdvanceStatus -> "advance status"
    is ButtonAction.SetProperty -> "set property"
}

@Composable
private fun StepScaffold(
    title: String,
    draft: BuilderDraft,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        draft.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        if (draft.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { content() }
        action?.let { (label, onClick) ->
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

@Composable
private fun SelectableRow(text: String, selected: Boolean, onClick: () -> Unit) {
    // Highlight the whole selected row (not just the radio dot) so the choice is
    // obvious regardless of the device's dynamic-color contrast.
    val background =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val textColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
