package com.example.hackchaton_2026_crm.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

class ScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads the text in a photo with ML Kit's on-device recognizer (the model ships inside the APK),
 * so photos of IDs and forms are never uploaded anywhere. Returns the text as rows, top to bottom.
 */
object TextReader {
    private const val TIMEOUT_MS = 20_000L

    suspend fun rows(context: Context, uri: Uri): List<String> = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: IOException) {
                throw ScanException("Couldn't open that photo", e)
            }
            val text = try {
                withTimeout(TIMEOUT_MS) { Tasks.await(recognizer.process(image)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw ScanException("Couldn't read the photo. Try again in better light.", e)
            }
            rows(text)
        } finally {
            recognizer.close()
        }
    }

    /**
     * ML Kit returns lines per block; a row of a form or card can be split across blocks, so put
     * every line back in reading order and join those that share a row ("Name" beside "Tan Ah Kow").
     */
    internal fun rows(text: Text): List<String> {
        class Piece(val text: String, val left: Int, val centerY: Int, val height: Int)
        val pieces = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            Piece(line.text, box.left, box.centerY(), box.height().coerceAtLeast(1))
        }.sortedBy { it.centerY }

        val rows = mutableListOf<MutableList<Piece>>()
        for (p in pieces) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(p.centerY - row.map { it.centerY }.average()) < row.maxOf { it.height } * 0.5) {
                row += p
            } else rows += mutableListOf(p)
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}
