package com.example.proxyscanner.core.importer

import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.parser.ConfigParser
import com.example.proxyscanner.core.parser.DefaultFormatDetector
import com.example.proxyscanner.core.parser.FormatDetector
import com.example.proxyscanner.core.parser.StrictUtf8
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a user-selected local document or an HTTPS download, decodes it as UTF-8 text and parses it.
 *
 * - Bounded: the reader stops at [ImportLimits.maxBytes]; the parser applies the
 *   entry and line limits.
 * - Strict: invalid UTF-8, NUL bytes (binary or UTF-16 files) and empty or
 *   whitespace-only files are failures, never silent successes.
 * - Honest: a file that yields no usable configuration is a failure
 *   ([ImportFailureCode.NO_VALID_CONFIGS]) with the parse report attached.
 * - Remote URLs are downloaded through [fetcher] (bounded, HTTPS only; see [HttpsFetcher]). Without a
 *   fetcher a remote import fails with [ImportFailureCode.NOT_IMPLEMENTED].
 * Messages are fixed text; file or response content and URLs never appear in them.
 */
class DefaultConfigImporter(
    private val documents: DocumentReader,
    private val parser: ConfigParser,
    private val detector: FormatDetector = DefaultFormatDetector(),
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fetcher: RemoteFetcher? = null,
) : ConfigImporter {

    override suspend fun import(source: ImportSource, limits: ImportLimits): ImportOutcome =
        when (source) {
            is ImportSource.RemoteUrl -> importRemote(source, limits)
            is ImportSource.LocalDocument -> importDocument(source, limits)
        }

    private suspend fun importDocument(source: ImportSource.LocalDocument, limits: ImportLimits): ImportOutcome {
        val read = documents.read(source.uri, limits.maxBytes)
        val bytes = when (read) {
            is DocumentReadResult.Content -> read.bytes
            DocumentReadResult.TooLarge -> return failure(
                ImportFailureCode.TOO_LARGE,
                "The file is larger than ${limits.maxBytes / (1024 * 1024)} MiB.",
            )
            DocumentReadResult.Unreadable -> return failure(
                ImportFailureCode.READ_ERROR,
                "The file could not be opened or read.",
            )
        }
        if (bytes.isEmpty()) {
            return failure(ImportFailureCode.EMPTY_CONTENT, "The file is empty.")
        }
        return withContext(parseDispatcher) { decodeAndParse(bytes, limits) }
    }

    private suspend fun importRemote(source: ImportSource.RemoteUrl, limits: ImportLimits): ImportOutcome {
        val remote = fetcher ?: return failure(
            ImportFailureCode.NOT_IMPLEMENTED,
            "Importing from a URL is not available in this version.",
        )
        return when (val fetched = remote.fetch(source.url, limits.maxBytes)) {
            is FetchResult.Body ->
                if (fetched.bytes.isEmpty()) {
                    failure(ImportFailureCode.EMPTY_CONTENT, "The response was empty.")
                } else {
                    withContext(parseDispatcher) { decodeAndParse(fetched.bytes, limits, "response") }
                }
            FetchResult.TooLarge -> failure(
                ImportFailureCode.TOO_LARGE,
                "The response is larger than ${limits.maxBytes / (1024 * 1024)} MiB.",
            )
            is FetchResult.HttpError -> failure(
                ImportFailureCode.HTTP_ERROR,
                "The server answered with HTTP ${fetched.status}.",
            )
            FetchResult.Timeout -> failure(ImportFailureCode.TIMEOUT, "The server did not answer in time.")
            FetchResult.NetworkError -> failure(
                ImportFailureCode.NETWORK_ERROR,
                "The connection failed. Check the address and your network.",
            )
            FetchResult.TooManyRedirects -> failure(
                ImportFailureCode.TOO_MANY_REDIRECTS,
                "The address redirected too many times.",
            )
            is FetchResult.Rejected -> failure(fetched.code, fetched.reason)
        }
    }

    /** [subject] is "file" or "response"; it only changes the wording of the fixed messages. */
    private fun decodeAndParse(bytes: ByteArray, limits: ImportLimits, subject: String = "file"): ImportOutcome {
        if (bytes.any { it == 0.toByte() }) {
            return failure(
                ImportFailureCode.INVALID_ENCODING,
                "The $subject does not look like UTF-8 text (it contains binary data).",
            )
        }
        val text = StrictUtf8.decode(bytes)
            ?: return failure(
                ImportFailureCode.INVALID_ENCODING,
                "The $subject is not valid UTF-8 text." + if (subject == "file") " Save it as UTF-8 and try again." else "",
            )
        if (text.isBlank()) {
            return failure(ImportFailureCode.EMPTY_CONTENT, "The $subject contains only whitespace.")
        }
        val result = parser.parse(text, limits)
        if (result.configs.isEmpty()) {
            return ImportOutcome.Failure(
                ImportFailure(
                    code = ImportFailureCode.NO_VALID_CONFIGS,
                    safeMessage = "No supported configuration was found in this $subject.",
                    details = result,
                ),
            )
        }
        return ImportOutcome.Success(result, detector.detect(text))
    }

    private fun failure(code: ImportFailureCode, message: String): ImportOutcome =
        ImportOutcome.Failure(ImportFailure(code, message))
}
