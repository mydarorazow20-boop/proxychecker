package com.example.proxyscanner.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.proxyscanner.core.importer.DocumentReadResult
import com.example.proxyscanner.core.importer.DocumentReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Reads documents chosen through the Storage Access Framework. Access is the
 * one-time grant that comes with the picked URI; no storage permission is used
 * and nothing is persisted.
 */
class ContentResolverDocumentReader(
    private val resolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentReader {

    override suspend fun displayName(uri: String): String? = withContext(ioDispatcher) {
        try {
            resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun read(uri: String, maxBytes: Long): DocumentReadResult = withContext(ioDispatcher) {
        try {
            val stream = resolver.openInputStream(Uri.parse(uri))
            if (stream == null) {
                DocumentReadResult.Unreadable
            } else {
                stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    var tooLarge = false
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) {
                            tooLarge = true
                            break
                        }
                        out.write(buffer, 0, count)
                    }
                    if (tooLarge) DocumentReadResult.TooLarge else DocumentReadResult.Content(out.toByteArray())
                }
            }
        } catch (e: IOException) {
            DocumentReadResult.Unreadable
        } catch (e: SecurityException) {
            DocumentReadResult.Unreadable
        } catch (e: IllegalArgumentException) {
            DocumentReadResult.Unreadable
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
