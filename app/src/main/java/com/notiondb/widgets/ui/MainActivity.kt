package com.notiondb.widgets.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiondb.widgets.App
import com.notiondb.widgets.ui.theme.NotionDbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as App).container

        setContent {
            NotionDbTheme {
                val vm: ConnectViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ConnectViewModel(container.authProvider, container.notionClient) as T
                    },
                )
                val state by vm.state.collectAsState()

                val onOAuth: (() -> Unit)? = if (container.oauthManager.isConfigured) {
                    {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(container.oauthManager.authorizeUrl())),
                        )
                    }
                } else null

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConnectScreen(
                        state = state,
                        onConnect = vm::connect,
                        modifier = Modifier.padding(innerPadding),
                        onConnectWithNotion = onOAuth,
                    )
                }
            }
        }
    }
}
