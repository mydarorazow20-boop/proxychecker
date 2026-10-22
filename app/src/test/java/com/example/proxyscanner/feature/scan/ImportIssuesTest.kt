package com.example.proxyscanner.feature.scan

import com.example.proxyscanner.core.model.ImportResult
import com.example.proxyscanner.core.model.ImportStats
import com.example.proxyscanner.core.model.ParseError
import com.example.proxyscanner.core.model.ParseErrorCode
import com.example.proxyscanner.core.model.UnsupportedEntry
import com.example.proxyscanner.core.model.UnsupportedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportIssuesTest {

    private val stats = ImportStats(0, 0, 0, 0, 0)

    private fun result(errors: List<ParseError> = emptyList(), unsupported: List<UnsupportedEntry> = emptyList()) =
        ImportResult(emptyList(), emptyList(), unsupported, errors, stats)

    @Test
    fun issuesAreSortedByLineAndCapped() {
        val errors = (1..12).map { ParseError(13 - it, ParseErrorCode.MALFORMED_URI, "bad $it") }
        val report = ImportIssues.report(result(errors = errors))
        assertEquals(ImportIssues.MAX_LISTED, report.issues.size)
        assertEquals(2, report.omittedIssues)
        assertEquals(1, report.issues.first().line)
    }

    @Test
    fun unknownSchemesAreNotEchoed() {
        val secretLikeScheme = "pw123secret"
        val entry = UnsupportedEntry(4, UnsupportedReason.UNKNOWN_SCHEME, secretLikeScheme)
        val text = ImportIssues.describe(entry)
        assertFalse(text.contains(secretLikeScheme))
        assertTrue(ImportIssues.describe(UnsupportedEntry(1, UnsupportedReason.UNKNOWN_SCHEME, "wireguard")).contains("wireguard"))
        assertTrue(ImportIssues.describe(UnsupportedEntry(1, UnsupportedReason.PROTOCOL_NOT_IMPLEMENTED, "hysteria2")).contains("hysteria2"))
    }
}
