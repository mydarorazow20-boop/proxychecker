package com.example.proxyscanner.core.importer

import com.example.proxyscanner.core.model.ImportLimits
import com.example.proxyscanner.core.parser.DefaultConfigParser
import com.example.proxyscanner.core.parser.InputFormat
import com.example.proxyscanner.testutil.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeDocumentReader(private val result: DocumentReadResult) : DocumentReader {
    var lastMaxBytes: Long = -1
    override suspend fun displayName(uri: String): String? = "list.txt"
    override suspend fun read(uri: String, maxBytes: Long): DocumentReadResult {
        lastMaxBytes = maxBytes
        return result
    }
}

private class FakeFetcher(private val result: FetchResult) : RemoteFetcher {
    var lastUrl: String? = null
    var lastMaxBytes: Long = -1
    var calls = 0
    override suspend fun fetch(url: String, maxBytes: Long): FetchResult {
        calls++
        lastUrl = url
        lastMaxBytes = maxBytes
        return result
    }
}

class DefaultConfigImporterTest {

    private val limits = ImportLimits()

    private fun importer(result: DocumentReadResult) =
        DefaultConfigImporter(FakeDocumentReader(result), DefaultConfigParser(), parseDispatcher = Dispatchers.Unconfined)

    private fun content(text: String) = DocumentReadResult.Content(text.toByteArray(Charsets.UTF_8))

    private val doc = ImportSource.LocalDocument("content://example/doc", "list.txt")

    private fun failureOf(outcome: ImportOutcome): ImportFailure {
        if (outcome !is ImportOutcome.Failure) fail("expected Failure but was $outcome")
        return (outcome as ImportOutcome.Failure).failure
    }

    @Test
    fun plainListIsImported() = runTest {
        val outcome = importer(content(Fixtures.vless() + "\n" + Fixtures.trojan())).import(doc, limits)
        assertTrue(outcome is ImportOutcome.Success)
        outcome as ImportOutcome.Success
        assertEquals(2, outcome.result.configs.size)
        assertEquals(InputFormat.PLAIN_LINK_LIST, outcome.format)
    }

    @Test
    fun base64ListIsImported() = runTest {
        val outcome = importer(content(Fixtures.b64(Fixtures.vless()))).import(doc, limits)
        outcome as ImportOutcome.Success
        assertEquals(InputFormat.BASE64_LINK_LIST, outcome.format)
        assertEquals(1, outcome.result.configs.size)
    }

    @Test
    fun utf8BomIsIgnored() = runTest {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + Fixtures.vless().toByteArray(Charsets.UTF_8)
        val outcome = importer(DocumentReadResult.Content(bytes)).import(doc, limits)
        assertTrue(outcome is ImportOutcome.Success)
    }

    @Test
    fun readerReceivesTheConfiguredSizeLimit() = runTest {
        val reader = FakeDocumentReader(content(Fixtures.vless()))
        DefaultConfigImporter(reader, DefaultConfigParser(), parseDispatcher = Dispatchers.Unconfined)
            .import(doc, ImportLimits(maxBytes = 1234))
        assertEquals(1234L, reader.lastMaxBytes)
    }

    @Test
    fun emptyFileIsAFailure() = runTest {
        val f = failureOf(importer(DocumentReadResult.Content(ByteArray(0))).import(doc, limits))
        assertEquals(ImportFailureCode.EMPTY_CONTENT, f.code)
    }

    @Test
    fun whitespaceOnlyFileIsAFailure() = runTest {
        val f = failureOf(importer(content(" \n\t\n ")).import(doc, limits))
        assertEquals(ImportFailureCode.EMPTY_CONTENT, f.code)
    }

    @Test
    fun invalidUtf8IsAFailure() = runTest {
        val f = failureOf(importer(DocumentReadResult.Content(byteArrayOf(0xC3.toByte(), 0x28, 0x41))).import(doc, limits))
        assertEquals(ImportFailureCode.INVALID_ENCODING, f.code)
    }

    @Test
    fun binaryDataWithNulBytesIsAFailure() = runTest {
        val f = failureOf(importer(DocumentReadResult.Content(byteArrayOf(0x50, 0x4B, 0x00, 0x03))).import(doc, limits))
        assertEquals(ImportFailureCode.INVALID_ENCODING, f.code)
    }

    @Test
    fun utf16TextIsRejectedInsteadOfMisread() = runTest {
        val utf16 = Fixtures.vless().toByteArray(Charsets.UTF_16LE)
        val f = failureOf(importer(DocumentReadResult.Content(utf16)).import(doc, limits))
        assertEquals(ImportFailureCode.INVALID_ENCODING, f.code)
    }

    @Test
    fun tooLargeAndUnreadableDocumentsAreFailures() = runTest {
        assertEquals(ImportFailureCode.TOO_LARGE, failureOf(importer(DocumentReadResult.TooLarge).import(doc, limits)).code)
        assertEquals(ImportFailureCode.READ_ERROR, failureOf(importer(DocumentReadResult.Unreadable).import(doc, limits)).code)
    }

    @Test
    fun fileWithoutUsableConfigsIsNotReportedAsSuccess() = runTest {
        val f = failureOf(importer(content("wireguard://x@y.invalid:1\nnot a link")).import(doc, limits))
        assertEquals(ImportFailureCode.NO_VALID_CONFIGS, f.code)
        val details = f.details
        assertNotNull(details)
        assertEquals(1, details!!.stats.unsupported)
        assertEquals(1, details.stats.errors)
    }

    @Test
    fun jsonFileIsNotReportedAsSuccess() = runTest {
        val f = failureOf(importer(content("{\"a\":1}")).import(doc, limits))
        assertEquals(ImportFailureCode.NO_VALID_CONFIGS, f.code)
    }

    @Test
    fun remoteUrlWithoutAFetcherIsNotImplemented() = runTest {
        val f = failureOf(importer(DocumentReadResult.Unreadable).import(ImportSource.RemoteUrl("https://example.invalid/x"), limits))
        assertEquals(ImportFailureCode.NOT_IMPLEMENTED, f.code)
        assertFalse(f.safeMessage.contains("example.invalid"))
        assertNull(f.details)
    }

    // ---- remote URL import (fake fetcher, no network) ----

    private val remote = ImportSource.RemoteUrl("https://lists.example.com/sub.txt?token=abc123")

    private fun remoteImporter(result: FetchResult, fetcher: FakeFetcher = FakeFetcher(result)) =
        DefaultConfigImporter(
            FakeDocumentReader(DocumentReadResult.Unreadable),
            DefaultConfigParser(),
            parseDispatcher = Dispatchers.Unconfined,
            fetcher = fetcher,
        )

    private fun body(text: String) = FetchResult.Body(text.toByteArray(Charsets.UTF_8))

    @Test
    fun downloadedListIsParsedWithAccurateCounts() = runTest {
        val text = listOf(
            Fixtures.vless(),
            Fixtures.vless(), // duplicate
            Fixtures.trojan(),
            "wireguard://x@y.invalid:1", // unsupported
            "not a link", // invalid
        ).joinToString("\n")
        val outcome = remoteImporter(body(text)).import(remote, limits)
        outcome as ImportOutcome.Success
        assertEquals(2, outcome.result.configs.size)
        assertEquals(1, outcome.result.stats.duplicates)
        assertEquals(1, outcome.result.stats.unsupported)
        assertEquals(1, outcome.result.stats.errors)
        assertEquals(InputFormat.PLAIN_LINK_LIST, outcome.format)
    }

    @Test
    fun downloadedBase64ListIsImported() = runTest {
        val outcome = remoteImporter(body(Fixtures.b64(Fixtures.vless() + "\n" + Fixtures.trojan()))).import(remote, limits)
        outcome as ImportOutcome.Success
        assertEquals(InputFormat.BASE64_LINK_LIST, outcome.format)
        assertEquals(2, outcome.result.configs.size)
    }

    @Test
    fun fetcherReceivesTheUrlAndTheConfiguredSizeLimit() = runTest {
        val fetcher = FakeFetcher(body(Fixtures.vless()))
        remoteImporter(body(""), fetcher).import(remote, ImportLimits(maxBytes = 1234))
        assertEquals(remote.url, fetcher.lastUrl)
        assertEquals(1234L, fetcher.lastMaxBytes)
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun fetchFailuresMapToImportFailureCodes() = runTest {
        val expected = listOf(
            FetchResult.TooLarge to ImportFailureCode.TOO_LARGE,
            FetchResult.HttpError(404) to ImportFailureCode.HTTP_ERROR,
            FetchResult.HttpError(503) to ImportFailureCode.HTTP_ERROR,
            FetchResult.Timeout to ImportFailureCode.TIMEOUT,
            FetchResult.NetworkError to ImportFailureCode.NETWORK_ERROR,
            FetchResult.TooManyRedirects to ImportFailureCode.TOO_MANY_REDIRECTS,
            FetchResult.Rejected(ImportFailureCode.PRIVATE_ADDRESS_BLOCKED, "blocked") to ImportFailureCode.PRIVATE_ADDRESS_BLOCKED,
            FetchResult.Rejected(ImportFailureCode.UNSUPPORTED_SCHEME, "scheme") to ImportFailureCode.UNSUPPORTED_SCHEME,
        )
        for ((fetched, code) in expected) {
            assertEquals(code, failureOf(remoteImporter(fetched).import(remote, limits)).code)
        }
    }

    @Test
    fun httpErrorMessageShowsOnlyTheStatus() = runTest {
        val f = failureOf(remoteImporter(FetchResult.HttpError(404)).import(remote, limits))
        assertTrue(f.safeMessage.contains("404"))
    }

    @Test
    fun emptyResponseIsAFailure() = runTest {
        val f = failureOf(remoteImporter(FetchResult.Body(ByteArray(0))).import(remote, limits))
        assertEquals(ImportFailureCode.EMPTY_CONTENT, f.code)
        assertEquals(ImportFailureCode.EMPTY_CONTENT, failureOf(remoteImporter(body(" \n\t ")).import(remote, limits)).code)
    }

    @Test
    fun invalidTextInAResponseIsAFailure() = runTest {
        val badUtf8 = FetchResult.Body(byteArrayOf(0xC3.toByte(), 0x28, 0x41))
        assertEquals(ImportFailureCode.INVALID_ENCODING, failureOf(remoteImporter(badUtf8).import(remote, limits)).code)
        val binary = FetchResult.Body(byteArrayOf(0x50, 0x4B, 0x00, 0x03))
        assertEquals(ImportFailureCode.INVALID_ENCODING, failureOf(remoteImporter(binary).import(remote, limits)).code)
    }

    @Test
    fun htmlOrMalformedResponseIsNotReportedAsSuccess() = runTest {
        val f = failureOf(remoteImporter(body("<html><body>Login required</body></html>")).import(remote, limits))
        assertEquals(ImportFailureCode.NO_VALID_CONFIGS, f.code)
        assertNotNull(f.details)
        val malformed = failureOf(remoteImporter(body("vless://broken\ntrojan://also-broken")).import(remote, limits))
        assertEquals(ImportFailureCode.NO_VALID_CONFIGS, malformed.code)
        assertTrue(malformed.details!!.stats.errors >= 1)
    }

    @Test
    fun remoteFailureMessagesNeverContainTheUrlOrContent() = runTest {
        val results = listOf(
            FetchResult.TooLarge,
            FetchResult.HttpError(500),
            FetchResult.Timeout,
            FetchResult.NetworkError,
            FetchResult.TooManyRedirects,
            body("garbage ${Fixtures.PASSWORD}"),
            body("<html>${Fixtures.PASSWORD}</html>"),
        )
        for (fetched in results) {
            val f = failureOf(remoteImporter(fetched).import(remote, limits))
            for (text in listOf(f.safeMessage) + (f.details?.errors?.map { it.safeMessage } ?: emptyList())) {
                assertFalse(text.contains("lists.example.com"))
                assertFalse(text.contains("abc123"))
                assertFalse(text.contains(Fixtures.PASSWORD))
            }
        }
    }

    @Test
    fun failureMessagesNeverContainContent() = runTest {
        val outcome = importer(content("garbage ${Fixtures.PASSWORD}")).import(doc, limits)
        val f = failureOf(outcome)
        assertFalse(f.safeMessage.contains(Fixtures.PASSWORD))
        for (e in f.details!!.errors) assertFalse(e.safeMessage.contains(Fixtures.PASSWORD))
    }
}
