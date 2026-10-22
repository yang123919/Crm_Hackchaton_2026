package com.example.hackchaton_2026_crm.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

class IcScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads a MyKad photo with ML Kit's on-device text recognizer (the model ships inside the APK),
 * so the picture is never uploaded anywhere.
 */
object IcScanner {
    private const val TIMEOUT_MS = 20_000L

    suspend fun scan(context: Context, uri: Uri): IcInfo = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: IOException) {
                throw IcScanException("Couldn't open that photo", e)
            }
            val text = try {
                withTimeout(TIMEOUT_MS) { Tasks.await(recognizer.process(image)) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw IcScanException("Couldn't read the photo. Try again in better light.", e)
            }
            MyKadParser.parse(rows(text))
        } finally {
            recognizer.close()
        }
    }

    /**
     * ML Kit returns lines per block; the card's rows can be split across blocks, so put every line
     * back in reading order and join those that share a row (e.g. an address line and "PEREMPUAN").
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
