package com.notiondb.widgets.data

import com.notiondb.widgets.auth.NotionAuthProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Thin Notion REST client.
 *
 * Pinned to API version [NOTION_VERSION] = 2026-04-01, which is the release
 * that exposes the Views API (read a view's saved filters/sorts and query
 * through it) plus the `me` and relative-date smart filters. Those are central
 * to the "reuse an existing Notion view as a widget" plan, so we target this
 * version deliberately rather than a floating default.
 *
 * Phase 0 only implements [validateToken]. Phase 1 adds: search databases,
 * list a database's data sources, list views, read view filters/sorts, and
 * query a data source. All requests must share a ~3 req/s rate limiter and
 * honour HTTP 429 `Retry-After` — wired in alongside the WorkManager refresh.
 */
class NotionClient(
    private val auth: NotionAuthProvider,
    private val http: HttpClient = defaultHttpClient(),
) {

    /** Calls GET /v1/users/me to confirm the stored token works. */
    suspend fun validateToken(): NotionResult<NotionUser> {
        val token = auth.currentToken()
            ?: return NotionResult.Failure(reason = "Not connected", retryable = false)

        return try {
            val response: HttpResponse = http.get("$BASE_URL/v1/users/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(NOTION_VERSION_HEADER, NOTION_VERSION)
            }
            when {
                response.status.isSuccess() ->
                    NotionResult.Success(response.body<NotionUser>())

                response.status == HttpStatusCode.TooManyRequests ->
                    NotionResult.Failure("Rate limited", retryable = true)

                response.status == HttpStatusCode.Unauthorized ->
                    NotionResult.Failure("Invalid token", retryable = false)

                else -> {
                    val err = runCatching { response.body<NotionError>() }.getOrNull()
                    NotionResult.Failure(err?.message ?: "HTTP ${response.status.value}", retryable = false)
                }
            }
        } catch (t: Throwable) {
            NotionResult.Failure(t.message ?: "Network error", retryable = true)
        }
    }

    companion object {
        const val BASE_URL = "https://api.notion.com"
        const val NOTION_VERSION_HEADER = "Notion-Version"
        const val NOTION_VERSION = "2026-04-01"

        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
