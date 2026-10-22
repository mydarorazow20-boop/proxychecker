package com.example.proxyscanner.core.importer

/**
 * Outcome of one bounded HTTPS download. It never carries the URL, headers or
 * body text, so it is safe to turn into a user-visible message.
 */
sealed interface FetchResult {
    /** The complete response body (2xx), at most the requested number of bytes. */
    class Body(val bytes: ByteArray) : FetchResult

    /** The body is larger than the allowed number of bytes (reading stopped early). */
    data object TooLarge : FetchResult

    /** The server answered with a non-success status that is not a followed redirect. */
    data class HttpError(val status: Int) : FetchResult

    data object Timeout : FetchResult

    /** DNS, connection, TLS or I/O failure. Details are deliberately not exposed. */
    data object NetworkError : FetchResult

    data object TooManyRedirects : FetchResult

    /** The URL (or a redirect target or resolved address) is not acceptable. [reason] is fixed text. */
    data class Rejected(val code: ImportFailureCode, val reason: String) : FetchResult
}

/**
 * Downloads a remote configuration list. Implementations must enforce HTTPS only,
 * timeouts, the [maxBytes] limit and coroutine cancellation, must never log the
 * URL or the content, and must never throw for network problems.
 */
interface RemoteFetcher {
    suspend fun fetch(url: String, maxBytes: Long): FetchResult
}
