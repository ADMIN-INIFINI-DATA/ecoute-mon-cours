package com.infinidata.ecoutemoncours.ingest

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * OCR imprime, 100 % hors-ligne, via ML Kit (modele latin).
 * Ne sait pas lire le manuscrit : voir GeminiClient pour ce cas.
 */
object MlKitOcr {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text -> cont.resume(toResult(text)) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /** Variante prenant directement un Uri : ML Kit gere alors l'orientation EXIF tout seul.
     *  Le decodage de l'image se fait hors du thread principal. */
    suspend fun recognizeUri(context: android.content.Context, uri: android.net.Uri): OcrResult =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromFilePath(context, uri)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text -> cont.resume(toResult(text)) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
        }

    /** Libere les ressources natives du reconnaisseur. */
    fun release() { runCatching { recognizer.close() } }

    /**
     * ML Kit ne trie pas les blocs : sur un cours en deux colonnes il renvoie n'importe quel ordre.
     * On retrie geometriquement, en detectant une eventuelle mise en colonnes.
     */
    private fun toResult(text: Text): OcrResult {
        val blocks = text.textBlocks.filter { it.boundingBox != null }
        if (blocks.isEmpty()) return OcrResult("", 0f)

        val pageWidth = blocks.maxOf { it.boundingBox!!.right }
        val leftColumn = blocks.count { (it.boundingBox!!.centerX()) < pageWidth * 0.45 }
        val rightColumn = blocks.count { (it.boundingBox!!.centerX()) > pageWidth * 0.55 }
        val twoColumns = leftColumn >= 2 && rightColumn >= 2

        val ordered = if (twoColumns) {
            val mid = pageWidth / 2
            val left = blocks.filter { it.boundingBox!!.centerX() < mid }.sortedBy { it.boundingBox!!.top }
            val right = blocks.filter { it.boundingBox!!.centerX() >= mid }.sortedBy { it.boundingBox!!.top }
            left + right
        } else {
            blocks.sortedWith(compareBy({ it.boundingBox!!.top / 20 }, { it.boundingBox!!.left }))
        }

        val sb = StringBuilder()
        ordered.forEach { block ->
            block.lines.forEach { line -> sb.append(line.text).append('\n') }
            sb.append('\n')
        }

        // Indice de fiabilite maison : proportion de caracteres alphabetiques et longueur des mots.
        val out = sb.toString()
        val letters = out.count { it.isLetter() }.toFloat()
        val confidence = if (out.isEmpty()) 0f else (letters / out.length)
        return OcrResult(out, confidence)
    }
}

data class OcrResult(val text: String, val confidence: Float) {
    /** En dessous de ce seuil, la page est probablement manuscrite ou illisible. */
    val looksUnreliable: Boolean get() = text.trim().length < 40 || confidence < 0.55f
}
