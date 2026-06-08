package com.notiondb.widgets.data

import com.notiondb.widgets.auth.NotionAuthProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [NotionClient.queryView]'s sort fallback. A view's saved
 * sort can reference a property the data-source query endpoint can't sort by
 * (e.g. a rollup/relation), which returns HTTP 400 — verified live with the
 * "This week" view. The widget must still show rows by retrying without sorts.
 */
class NotionClientViewQueryTest {

    private val viewId = "view-1"
    private val dataSourceId = "ds-1"

    private val viewDetail = """
        {"object":"view","id":"$viewId","data_source_id":"$dataSourceId",
         "name":"This week","type":"table","filter":null,
         "sorts":[{"property":"QgDl","direction":"descending"}]}
    """.trimIndent()

    private val rowsBody = """
        {"results":[{"id":"p-1","properties":{
          "Name":{"type":"title","title":[{"plain_text":"Morning prayer"}]}}}],
         "has_more":false}
    """.trimIndent()

    private val error400 =
        """{"object":"error","status":400,"code":"validation_error","message":"Could not find sort property"}"""

    private val auth = object : NotionAuthProvider {
        override val isAuthenticated: Flow<Boolean> = flowOf(true)
        override suspend fun currentToken(): String = "tok"
        override suspend fun signOut() {}
    }

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        Triple(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    /** Builds a client whose query endpoint responds based on whether sorts are present. */
    private fun client(onQueryWithSorts: HttpStatusCode, queryCounter: IntArray): NotionClient {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val body = (request.body as? TextContent)?.text.orEmpty()
            when {
                request.method == HttpMethod.Get && path.endsWith("/v1/views/$viewId") ->
                    respond(viewDetail, HttpStatusCode.OK, json(viewDetail).third)

                path.endsWith("/v1/data_sources/$dataSourceId/query") -> {
                    queryCounter[0]++
                    if (body.contains("\"sorts\"")) {
                        val b = if (onQueryWithSorts == HttpStatusCode.OK) rowsBody else error400
                        respond(b, onQueryWithSorts, json(b).third)
                    } else {
                        respond(rowsBody, HttpStatusCode.OK, json(rowsBody).third)
                    }
                }

                else -> respond("{}", HttpStatusCode.OK, json("{}").third)
            }
        }
        return NotionClient(auth = auth, http = HttpClient(engine), limiter = RateLimiter(1000))
    }

    @Test
    fun `falls back to unsorted query when the saved sort is rejected`() = runTest {
        val calls = intArrayOf(0)
        val result = client(onQueryWithSorts = HttpStatusCode.BadRequest, calls).queryView(viewId)

        assertTrue(result is NotionResult.Success)
        val pages = (result as NotionResult.Success).value.pages
        assertEquals("Morning prayer", (pages.single().properties["Name"] as PropertyValue.Text).value)
        // First query (with sorts) 400s, second (no sorts) succeeds.
        assertEquals(2, calls[0])
    }

    @Test
    fun `does not fall back when the sorted query succeeds`() = runTest {
        val calls = intArrayOf(0)
        val result = client(onQueryWithSorts = HttpStatusCode.OK, calls).queryView(viewId)

        assertTrue(result is NotionResult.Success)
        assertEquals(1, calls[0]) // no second, unsorted attempt
    }

    @Test
    fun `retryable failure is surfaced without dropping the sort`() = runTest {
        val calls = intArrayOf(0)
        val result = client(onQueryWithSorts = HttpStatusCode.TooManyRequests, calls).queryView(viewId)

        assertTrue(result is NotionResult.Failure)
        assertTrue((result as NotionResult.Failure).retryable)
        assertFalse("must not fall back on a transient error", calls[0] == 2)
        assertEquals(1, calls[0])
    }
}
