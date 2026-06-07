package com.notiondb.widgets.auth

import android.net.Uri

/**
 * Public-OAuth configuration (Phase 5). Fill these in after creating a public
 * Notion integration and deploying the Cloudflare Worker (see /worker/README).
 * Until both are set, the app hides the "Connect with Notion" button and the
 * paste-token flow remains the only path — so the token-only build keeps
 * working unchanged.
 */
object NotionOAuthConfig {
    /** OAuth client id from notion.com/my-integrations. */
    const val CLIENT_ID = ""

    /** Deployed Worker base URL, e.g. https://notion-db-widgets-oauth.<acct>.workers.dev */
    const val WORKER_BASE_URL = ""

    /** Must match the redirect URI registered on the Notion integration + manifest. */
    const val REDIRECT_URI = "notiondbwidgets://oauth"

    val isConfigured: Boolean
        get() = CLIENT_ID.isNotBlank() && WORKER_BASE_URL.isNotBlank()

    /** The Notion authorize URL the browser opens to start the flow. */
    fun authorizeUrl(): String =
        Uri.parse("https://api.notion.com/v1/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("owner", "user")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .build()
            .toString()

    fun tokenEndpoint(): String = WORKER_BASE_URL.trimEnd('/') + "/oauth/token"
}
