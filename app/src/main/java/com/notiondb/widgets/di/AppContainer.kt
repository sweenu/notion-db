package com.notiondb.widgets.di

import android.content.Context
import com.notiondb.widgets.auth.TokenAuthProvider
import com.notiondb.widgets.auth.TokenStore
import com.notiondb.widgets.data.NotionClient

/**
 * Hand-rolled dependency container. The app is small enough that a DI framework
 * (Hilt) would be overkill for the hobby-first scope; if it grows, swapping this
 * for Hilt is mechanical. Held as a singleton off [App].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val authProvider: TokenAuthProvider by lazy {
        TokenAuthProvider(TokenStore(appContext))
    }

    val notionClient: NotionClient by lazy {
        NotionClient(authProvider)
    }
}
