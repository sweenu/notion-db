package com.notiondb.widgets.auth

import com.notiondb.widgets.data.NotionClient
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Drives the OAuth code→token exchange via the Cloudflare Worker, then stores
 * the resulting access token in the shared [TokenStore]. Because a public
 * Notion access token is a normal bearer token (and doesn't expire), the rest
 * of the app keeps reading it through the same [NotionAuthProvider] — OAuth is
 * just a second way to populate the store, not a separate runtime path.
 */
class OAuthManager(
    private val store: TokenStore,
    private val http: HttpClient = NotionClient.defaultHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val isConfigured: Boolean get() = NotionOAuthConfig.isConfigured

    fun authorizeUrl(): String = NotionOAuthConfig.authorizeUrl()

    /** Exchange the [code] from the redirect for a token. Returns workspace name. */
    suspend fun exchangeCode(code: String): Result<String> {
        return try {
            val response = http.post(NotionOAuthConfig.tokenEndpoint()) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("code", code)
                        put("redirect_uri", NotionOAuthConfig.REDIRECT_URI)
                    }.toString(),
                )
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Token exchange failed (${response.status.value})"))
            }
            val obj = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return Result.failure(IllegalStateException("Malformed token response"))

            val token = obj["access_token"]?.jsonPrimitive?.content
                ?: return Result.failure(IllegalStateException("No access_token in response"))

            store.save(token)
            val workspace = obj["workspace_name"]?.jsonPrimitive?.content ?: "your workspace"
            Result.success(workspace)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
