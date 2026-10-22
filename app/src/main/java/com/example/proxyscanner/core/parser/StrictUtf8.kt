package com.example.proxyscanner.core.parser

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** UTF-8 decoding that reports malformed input instead of substituting characters. */
object StrictUtf8 {

    /** Returns null when [bytes] are not valid UTF-8. A leading BOM is removed. */
    fun decode(bytes: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        return if (text.startsWith("\uFEFF")) text.substring(1) else text
    }
}
