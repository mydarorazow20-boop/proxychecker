package com.example.proxyscanner.core.importer

import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.model.ImportResult
import com.example.proxyscanner.core.parser.InputFormat

sealed interface ImportSource {
    /** Document chosen through the Storage Access Framework (content:// URI as text). */
    data class LocalDocument(val uri: String, val displayName: String?) : ImportSource

    /** User-supplied HTTP(S) URL. Subject to [RemoteUrlPolicy]. */
    data class RemoteUrl(val url: String) : ImportSource
}

enum class ImportFailureCode {
    UNSUPPORTED_SCHEME,
    URL_NOT_ALLOWED,
    PRIVATE_ADDRESS_BLOCKED,
    TOO_MANY_REDIRECTS,
    TOO_LARGE,
    TIMEOUT,
    HTTP_ERROR,
    NETWORK_ERROR,
    READ_ERROR,
    EMPTY_CONTENT,
    INVALID_ENCODING,
    NO_VALID_CONFIGS,
    NOT_IMPLEMENTED,
}

/** [safeMessage] must be redacted; never include the full URL or query string. */
data class ImportFailure(
    val code: ImportFailureCode,
    val safeMessage: String,
    /** Parse report when the content was read and parsed but nothing usable was found. */
    val details: ImportResult? = null,
)

sealed interface ImportOutcome {
    data class Success(val result: ImportResult, val format: InputFormat) : ImportOutcome

    data class Failure(val failure: ImportFailure) : ImportOutcome
}

/** Reads, bounds, parses. Suspending; honors cancellation. */
interface ConfigImporter {
    suspend fun import(source: ImportSource, limits: ImportLimits): ImportOutcome
}

sealed interface UrlCheck {
    data object Allowed : UrlCheck

    data class Rejected(val code: ImportFailureCode, val reason: String) : UrlCheck
}

/**
 * Remote URL safety policy (docs/01-technical-design.md, section 9).
 * - Only http/https; no userinfo in the URL.
 * - The host is resolved and EVERY resolved address must be public: loopback,
 *   link-local, private (RFC 1918), unique-local IPv6, multicast, unspecified
 *   and IPv4-mapped forms of those are rejected.
 * - The address that was checked must be the address that is connected to
 *   (no second resolution), and every redirect is checked again.
 */
interface RemoteUrlPolicy {
    /** Syntax-level checks that need no network. */
    fun checkSyntax(url: String): UrlCheck

    /** Resolves [host] and checks all addresses. */
    suspend fun checkResolvedAddresses(host: String): UrlCheck
}
