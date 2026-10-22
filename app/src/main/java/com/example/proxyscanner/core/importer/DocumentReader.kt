package com.example.proxyscanner.core.importer

sealed interface DocumentReadResult {
    /** The complete content, at most the requested number of bytes. */
    class Content(val bytes: ByteArray) : DocumentReadResult

    /** The document is larger than the allowed number of bytes (reading stopped early). */
    data object TooLarge : DocumentReadResult

    /** The document could not be opened or read (missing, revoked access, I/O error). */
    data object Unreadable : DocumentReadResult
}

/**
 * Platform access to a user-selected document. The Android implementation uses
 * the ContentResolver; tests use a fake. Implementations must run blocking I/O off
 * the main thread, stop reading once [read]'s limit is exceeded, honor coroutine
 * cancellation, and never throw for unreadable documents.
 */
interface DocumentReader {
    /** Raw display name reported by the provider, or null if unavailable. */
    suspend fun displayName(uri: String): String?

    suspend fun read(uri: String, maxBytes: Long): DocumentReadResult
}
