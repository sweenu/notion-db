package com.notiondb.widgets.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "notion_auth")

/**
 * Persists the Notion token on-device via DataStore.
 *
 * NOTE (security hardening, post-Phase 0): the token is stored in plaintext
 * preferences for now. Before any public release, wrap writes/reads with a
 * key from the Android Keystore (or use an encrypted store) so the token is
 * encrypted at rest.
 */
class TokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("notion_token")

    val token: Flow<String?> = context.authDataStore.data.map { it[tokenKey] }

    suspend fun save(token: String) {
        context.authDataStore.edit { it[tokenKey] = token.trim() }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.remove(tokenKey) }
    }
}
