package com.notiondb.widgets.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes Notion calls to stay under the ~3 requests/second per-integration
 * limit. A single shared instance gates every request the client makes, so
 * widget refreshes and write-backs can't collectively blow past the quota.
 *
 * Simple spacing approach: enforce a minimum gap between successive calls.
 * Pair with [NotionClient]'s 429 handling, which additionally backs off when
 * the server says to.
 */
class RateLimiter(requestsPerSecond: Int = 3) {
    private val minIntervalMs = 1000L / requestsPerSecond
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun <T> acquire(block: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val wait = lastRequestAt + minIntervalMs - now
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        return block()
    }
}
