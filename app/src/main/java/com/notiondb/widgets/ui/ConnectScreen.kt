package com.notiondb.widgets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Phase 0 onboarding: paste a Notion internal-integration token and verify it.
 * Phase 5 will add a "Connect with Notion" OAuth button alongside this, sharing
 * the same NotionAuthProvider contract.
 */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onConnectWithNotion: (() -> Unit)? = null,
) {
    var token by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connect Notion", style = MaterialTheme.typography.headlineSmall)

        if (onConnectWithNotion != null) {
            Button(onClick = onConnectWithNotion, modifier = Modifier.fillMaxWidth()) {
                Text("Connect with Notion")
            }
            Text("— or paste a token —", style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "Create an internal integration at notion.so/my-integrations, share " +
                "your databases with it, then paste its token below.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Integration token (ntn_…)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onConnect(token) },
            enabled = state !is ConnectUiState.Checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect")
        }

        when (state) {
            is ConnectUiState.Checking -> CircularProgressIndicator()
            is ConnectUiState.Connected ->
                Text(
                    "Connected to ${state.workspace} 🎉",
                    color = MaterialTheme.colorScheme.primary,
                )
            is ConnectUiState.Error ->
                Text(state.message, color = MaterialTheme.colorScheme.error)
            ConnectUiState.Idle -> Unit
        }
    }
}
