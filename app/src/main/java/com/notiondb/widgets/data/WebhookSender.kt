package com.notiondb.widgets.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fires a user-defined webhook (an app-defined "button" action). This is the
 * closest 1:1 to a native Notion button's "Send webhook" action, and lets
 * people wire widgets into Zapier / Make / n8n.
 */
object WebhookSender {

    /** POSTs [jsonBody] to [url]. Returns true on a 2xx response. */
    suspend fun post(url: String, jsonBody: String): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
