package com.notiondb.widgets.data

/**
 * Result wrapper that carries whether a failure is worth retrying — the write
 * back queue and refresh worker (Phase 2) branch on [Failure.retryable] to
 * decide between backoff and giving up (e.g. 429 / network = retry, 401 = not).
 */
sealed interface NotionResult<out T> {
    data class Success<T>(val value: T) : NotionResult<T>
    data class Failure(val reason: String, val retryable: Boolean) : NotionResult<Nothing>
}
