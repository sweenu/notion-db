package com.notiondb.widgets.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * v1 auth: the user creates a Notion internal integration and pastes its token.
 * No backend required. Token refresh is a no-op because internal-integration
 * tokens do not expire.
 */
class TokenAuthProvider(private val store: TokenStore) : NotionAuthProvider {

    override val isAuthenticated: Flow<Boolean> =
        store.token.map { !it.isNullOrBlank() }

    override suspend fun currentToken(): String? = store.token.first()

    suspend fun connect(token: String) = store.save(token)

    override suspend fun signOut() = store.clear()
}
