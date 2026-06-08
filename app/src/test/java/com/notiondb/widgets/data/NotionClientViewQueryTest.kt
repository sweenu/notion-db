package com.notiondb.widgets.data

import com.notiondb.widgets.auth.NotionAuthProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [NotionClient.queryView]'s view-query flow: create the query, page
 * through the matched ids, then fetch each page. Notion evaluates the view's
 * real filter/sort, so the client just stitches ids → pages.
 */
class NotionClientViewQueryTest {

    private val viewId = "view-1"

    private val auth = object : NotionAuthProvider {
        override val isAuthenticated: Flow<Boolean> = flowOf(true)
        override suspend fun currentToken(): String = "tok"
        override suspend fun signOut() {}
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun page(id: String, title: String, status: String) = """
        {"object":"page","id":"$id","url":"https://notion.so/$id","properties":{
          "Name":{"type":"title","title":[{"plain_text":"$title"}]},
          "Status":{"type":"status","status":{"id":"s","name":"$status","color":"red"}}}}
    """.trimIndent()

    private fun client(engine: MockEngine) =
        NotionClient(auth = auth, http = HttpClient(engine), limiter = RateLimiter(1000))

    private fun titlesOf(r: NotionResult<NotionJson.PageQueryResult>): List<String> =
        (r as NotionResult.Success).value.pages.map { (it.properties["Name"] as PropertyValue.Text).value }

    @Test
    fun `view query fetches each matched page`() = runTest {
        val pages = mapOf("p1" to ("Morning prayer" to "Not done"), "p2" to ("Bible reading" to "Done"))
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            when {
                req.method == HttpMethod.Post && path.endsWith("/v1/views/$viewId/queries") ->
                    respond(
                        """{"object":"list","id":"q1","has_more":false,"next_cursor":null,
                            "results":[{"object":"page","id":"p1"},{"object":"page","id":"p2"}]}""",
                        HttpStatusCode.OK, jsonHeaders,
                    )
                path.startsWith("/v1/pages/") -> {
                    val id = path.substringAfterLast('/')
                    val (t, s) = pages.getValue(id)
                    respond(page(id, t, s), HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val result = client(engine).queryView(viewId)
        assertTrue(result is NotionResult.Success)
        assertEquals(listOf("Morning prayer", "Bible reading"), titlesOf(result))
    }

    @Test
    fun `view query pages through ids before fetching`() = runTest {
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            when {
                req.method == HttpMethod.Post && path.endsWith("/v1/views/$viewId/queries") ->
                    respond(
                        """{"object":"list","id":"q1","has_more":true,"next_cursor":"c2",
                            "results":[{"object":"page","id":"p1"}]}""",
                        HttpStatusCode.OK, jsonHeaders,
                    )
                req.method == HttpMethod.Get && path.endsWith("/v1/views/$viewId/queries/q1") ->
                    respond(
                        """{"object":"list","id":"q1","has_more":false,"next_cursor":null,
                            "results":[{"object":"page","id":"p2"}]}""",
                        HttpStatusCode.OK, jsonHeaders,
                    )
                path.startsWith("/v1/pages/") ->
                    respond(page(path.substringAfterLast('/'), "Row", "Not done"), HttpStatusCode.OK, jsonHeaders)
                else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val result = client(engine).queryView(viewId)
        assertEquals(2, (result as NotionResult.Success).value.pages.size)
    }

    @Test
    fun `a page that fails to load is skipped, the rest still render`() = runTest {
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            when {
                req.method == HttpMethod.Post && path.endsWith("/v1/views/$viewId/queries") ->
                    respond(
                        """{"object":"list","id":"q1","has_more":false,
                            "results":[{"object":"page","id":"ok"},{"object":"page","id":"gone"}]}""",
                        HttpStatusCode.OK, jsonHeaders,
                    )
                path.endsWith("/v1/pages/gone") ->
                    respond("""{"object":"error","status":404,"message":"Not found"}""", HttpStatusCode.NotFound, jsonHeaders)
                path.startsWith("/v1/pages/") ->
                    respond(page("ok", "Morning prayer", "Not done"), HttpStatusCode.OK, jsonHeaders)
                else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val result = client(engine).queryView(viewId)
        assertEquals(listOf("Morning prayer"), titlesOf(result))
    }
}
