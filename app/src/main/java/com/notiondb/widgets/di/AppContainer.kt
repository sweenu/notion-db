package com.notiondb.widgets.di

import android.content.Context
import com.notiondb.widgets.auth.OAuthManager
import com.notiondb.widgets.auth.TokenAuthProvider
import com.notiondb.widgets.auth.TokenStore
import com.notiondb.widgets.data.NotionClient
import com.notiondb.widgets.data.WidgetRepository
import com.notiondb.widgets.data.local.AppDatabase

/**
 * Hand-rolled dependency container. The app is small enough that a DI framework
 * (Hilt) would be overkill for the hobby-first scope; if it grows, swapping this
 * for Hilt is mechanical. Held as a singleton off [com.notiondb.widgets.App].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val tokenStore by lazy { TokenStore(appContext) }

    val authProvider: TokenAuthProvider by lazy {
        TokenAuthProvider(tokenStore)
    }

    val oauthManager: OAuthManager by lazy {
        OAuthManager(tokenStore)
    }

    val notionClient: NotionClient by lazy {
        NotionClient(authProvider)
    }

    private val database by lazy { AppDatabase.get(appContext) }

    val repository: WidgetRepository by lazy {
        WidgetRepository(notionClient, database.widgetDao())
    }
}
