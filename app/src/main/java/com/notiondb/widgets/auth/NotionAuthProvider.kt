package com.notiondb.widgets.auth

import kotlinx.coroutines.flow.Flow

/**
 * Source of a valid Notion API bearer token.
 *
 * The rest of the app depends only on this interface so the authentication
 * strategy can change without touching the data layer. v1 ships
 * [TokenAuthProvider] (the user pastes their own internal-integration token).
 * A future `OAuthAuthProvider` — backed by the Cloudflare Worker that holds the
 * client secret and performs code-exchange / refresh — will implement the same
 * contract, making distribution a drop-in addition rather than a rewrite.
 */
interface NotionAuthProvider {

    /** Emits whether a usable token is currently stored. */
    val isAuthenticated: Flow<Boolean>

    /**
     * Returns the current bearer token, or null if the user has not connected.
     * Implementations are responsible for refreshing expired tokens (a no-op for
     * the token-only provider, real work for the OAuth one).
     */
    suspend fun currentToken(): String?

    /** Forget the stored credentials. */
    suspend fun signOut()
}
