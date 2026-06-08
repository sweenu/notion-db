package com.notiondb.widgets.data

import com.notiondb.widgets.auth.NotionAuthProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Notion REST client, pinned to API version 2026-03-11 (the latest valid
 * version; data sources are available from 2025-09-03 on). Every request is
 * funneled through a shared [RateLimiter] and maps non-2xx responses to
 * [NotionResult.Failure] with a retryable flag.
 *
 * The view-related endpoints (list/query views) are not guaranteed to exist on
 * this version and should be verified against a live response; they're flagged
 * with TODO where the exact shape is assumed, and the builder always offers the
 * "whole database" path as a fallback.
 */
class NotionClient(
    private val auth: NotionAuthProvider,
    private val http: HttpClient = defaultHttpClient(),
    private val limiter: RateLimiter = RateLimiter(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {

    suspend fun validateToken(): NotionResult<NotionUser> =
        request { token -> http.get("$BASE_URL/v1/users/me") { auth(token) } }
            .map { json.decodeFromString(NotionUser.serializer(), it) }

    /**
     * POST /v1/search restricted to data sources. On the data-source API
     * (2025-09-03+) the search object filter is "data_source", not "database",
     * and each result is the queryable data source itself.
     */
    suspend fun searchDatabases(query: String = ""): NotionResult<List<NotionDatabase>> =
        request { token ->
            http.post("$BASE_URL/v1/search") {
                auth(token)
                jsonBody(buildJsonObject {
                    if (query.isNotBlank()) put("query", query)
                    putJsonObject("filter") {
                        put("value", "data_source")
                        put("property", "object")
                    }
                })
            }
        }.map { NotionJson.parseDataSources(it.asObject()) }

    /** GET /v1/data_sources/{id} — schema (property definitions). */
    suspend fun getSchema(dataSourceId: String): NotionResult<List<PropertySchema>> =
        request { token -> http.get("$BASE_URL/v1/data_sources/$dataSourceId") { auth(token) } }
            .map { NotionJson.parseSchema(it.asObject()) }

    /**
     * Lists a database's saved views. The list endpoint (GET /v1/views) returns
     * only `{object, id}` per view, so we retrieve each view to get its name and
     * type. Views that fail to load individually are skipped rather than failing
     * the whole picker.
     */
    suspend fun listViews(databaseId: String): NotionResult<List<NotionView>> {
        val ids = when (
            val r = request { token ->
                http.get("$BASE_URL/v1/views") {
                    auth(token)
                    parameter("database_id", databaseId)
                }
            }.map { NotionJson.parseViewIds(it.asObject()) }
        ) {
            is NotionResult.Success -> r.value
            is NotionResult.Failure -> return r
        }
        val views = ids.mapNotNull { id ->
            retrieveView(id).getOrNull()?.let { NotionView(it.id, it.name, it.type) }
        }
        return NotionResult.Success(views)
    }

    /** GET /v1/views/{id} — full view detail (name, type, data source, filter, sorts). */
    suspend fun retrieveView(viewId: String): NotionResult<NotionViewDetail> =
        request { token -> http.get("$BASE_URL/v1/views/$viewId") { auth(token) } }
            .map { NotionJson.parseViewDetail(it.asObject()) }

    /** Query a data source's rows, optionally with explicit filter/sorts. */
    suspend fun queryDataSource(
        dataSourceId: String,
        filter: JsonObject? = null,
        sorts: List<JsonObject> = emptyList(),
        startCursor: String? = null,
        pageSize: Int = 50,
    ): NotionResult<NotionJson.PageQueryResult> =
        request { token ->
            http.post("$BASE_URL/v1/data_sources/$dataSourceId/query") {
                auth(token)
                jsonBody(buildJsonObject {
                    put("page_size", pageSize)
                    startCursor?.let { put("start_cursor", it) }
                    filter?.let { put("filter", it) }
                    if (sorts.isNotEmpty()) putJsonArray("sorts") { sorts.forEach { add(it) } }
                })
            }
        }.map { NotionJson.parsePages(it.asObject()) }

    /**
     * Query a view's rows by mirroring its saved filter/sorts onto the data
     * source. There is no view-query endpoint on this API version, so we read
     * the view (data_source_id + filter + sorts) and run a normal data-source
     * query. The saved filter is passed best-effort (its schema matches the
     * query filter for the common cases).
     */
    suspend fun queryView(
        viewId: String,
        startCursor: String? = null,
        pageSize: Int = 50,
    ): NotionResult<NotionJson.PageQueryResult> {
        val view = when (val r = retrieveView(viewId)) {
            is NotionResult.Success -> r.value
            is NotionResult.Failure -> return r
        }

        val full = queryDataSource(
            dataSourceId = view.dataSourceId,
            filter = view.filter,
            sorts = view.sorts,
            startCursor = startCursor,
            pageSize = pageSize,
        )
        // A view's saved sort can reference a property the query endpoint can't
        // sort by (e.g. rollup/relation), which 400s. Rather than show an empty
        // widget, retry without the sorts (keeping any filter). Genuine
        // retryable failures (429/network) are returned as-is so the worker
        // backs off instead of silently dropping the sort.
        if (full is NotionResult.Success || (full is NotionResult.Failure && full.retryable)) {
            return full
        }
        return queryDataSource(
            dataSourceId = view.dataSourceId,
            filter = view.filter,
            sorts = emptyList(),
            startCursor = startCursor,
            pageSize = pageSize,
        )
    }

    /** PATCH a page's properties (write-back, Phase 2/3). */
    suspend fun updatePage(pageId: String, properties: JsonObject): NotionResult<Unit> =
        request { token ->
            http.patch("$BASE_URL/v1/pages/$pageId") {
                auth(token)
                jsonBody(buildJsonObject { put("properties", properties) })
            }
        }.map { }

    /** Create a page in a data source (Phase 3 "add row"). */
    suspend fun createPage(dataSourceId: String, properties: JsonObject): NotionResult<Unit> =
        request { token ->
            http.post("$BASE_URL/v1/pages") {
                auth(token)
                jsonBody(buildJsonObject {
                    putJsonObject("parent") {
                        put("type", "data_source_id")
                        put("data_source_id", dataSourceId)
                    }
                    put("properties", properties)
                })
            }
        }.map { }

    // --- request plumbing ---------------------------------------------------

    private fun HttpRequestBuilder.auth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(NOTION_VERSION_HEADER, NOTION_VERSION)
    }

    private fun HttpRequestBuilder.jsonBody(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }

    /** Runs [call] with auth, rate limiting, and status→Result mapping. */
    private suspend fun request(
        call: suspend (token: String) -> HttpResponse,
    ): NotionResult<String> {
        val token = auth.currentToken()
            ?: return NotionResult.Failure("Not connected", retryable = false)
        return try {
            limiter.acquire {
                val response = call(token)
                when {
                    response.status.isSuccess() ->
                        NotionResult.Success(response.bodyAsText())
                    response.status == HttpStatusCode.TooManyRequests ->
                        NotionResult.Failure("Rate limited", retryable = true)
                    response.status == HttpStatusCode.Unauthorized ->
                        NotionResult.Failure("Invalid or expired token", retryable = false)
                    response.status.value in 500..599 ->
                        NotionResult.Failure("Notion server error", retryable = true)
                    else -> {
                        val msg = runCatching {
                            json.decodeFromString(NotionError.serializer(), response.bodyAsText()).message
                        }.getOrNull()
                        NotionResult.Failure(msg ?: "HTTP ${response.status.value}", retryable = false)
                    }
                }
            }
        } catch (t: Throwable) {
            NotionResult.Failure(t.message ?: "Network error", retryable = true)
        }
    }

    private fun String.asObject(): JsonObject =
        json.parseToJsonElement(this) as? JsonObject ?: JsonObject(emptyMap())

    companion object {
        const val BASE_URL = "https://api.notion.com"
        const val NOTION_VERSION_HEADER = "Notion-Version"
        const val NOTION_VERSION = "2026-03-11"

        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
