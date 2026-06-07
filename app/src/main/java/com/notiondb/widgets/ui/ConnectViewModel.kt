package com.notiondb.widgets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notiondb.widgets.auth.TokenAuthProvider
import com.notiondb.widgets.data.NotionClient
import com.notiondb.widgets.data.NotionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectUiState {
    data object Idle : ConnectUiState
    data object Checking : ConnectUiState
    data class Connected(val workspace: String) : ConnectUiState
    data class Error(val message: String) : ConnectUiState
}

class ConnectViewModel(
    private val auth: TokenAuthProvider,
    private val client: NotionClient,
) : ViewModel() {

    private val _state = MutableStateFlow<ConnectUiState>(ConnectUiState.Idle)
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    /** Saves the pasted token, then verifies it against GET /v1/users/me. */
    fun connect(token: String) {
        if (token.isBlank()) {
            _state.value = ConnectUiState.Error("Paste your integration token first.")
            return
        }
        viewModelScope.launch {
            _state.value = ConnectUiState.Checking
            auth.connect(token)
            when (val result = client.validateToken()) {
                is NotionResult.Success -> {
                    val name = result.value.bot?.workspaceName
                        ?: result.value.name
                        ?: "your workspace"
                    _state.value = ConnectUiState.Connected(name)
                }
                is NotionResult.Failure -> {
                    auth.signOut()
                    _state.value = ConnectUiState.Error(result.reason)
                }
            }
        }
    }
}
