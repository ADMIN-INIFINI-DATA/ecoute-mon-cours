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

    /**
     * Vrai quand le texte reconnu n'a pas l'air d'etre du francais.
     *
     * Le piege du manuscrit : le modele ne renvoie pas une page vide, il renvoie du
     * charabia plausible — des suites de lettres de bonne longueur, avec un fort taux
     * alphabetique. Compter les caracteres ne suffit donc pas ; on cherche des mots
     * outils francais, qui representent normalement un mot sur cinq dans un cours.
     */
    val looksUnreliable: Boolean
        get() {
            val clean = text.trim()
            if (clean.length < 40 || confidence < 0.55f) return true

            val words = clean.lowercase()
                .split(Regex("[^\\p{L}']+"))
                .filter { it.isNotBlank() }
            if (words.size < 12) return true

            val stopWords = words.count { it in FRENCH_STOP_WORDS }
            // Les tokens d'une ou deux lettres sont ecartes : dans un enonce de maths,
            // « f de x » en produit beaucoup sans que la lecture soit fausse.
            val long = words.filter { it.length > 2 }
            val withVowel = long.count { w -> w.any { it in "aeiouyàâéèêëîïôöûüù" } }

            // Les DEUX signaux doivent etre au rouge : une liste de vocabulaire manque
            // de mots outils sans etre du charabia, et le charabia manque des deux.
            return stopWords * 20 < words.size && withVowel * 5 < long.size * 4
        }

    private companion object {
        val FRENCH_STOP_WORDS = setOf(
            "le", "la", "les", "un", "une", "des", "de", "du", "et", "est", "en", "dans",
            "qui", "que", "pour", "par", "sur", "au", "aux", "ce", "cette", "ces", "il",
            "elle", "on", "nous", "vous", "ils", "elles", "son", "sa", "ses", "leur",
            "plus", "pas", "ne", "se", "sont", "a", "ont", "avec", "comme", "mais", "ou",
            "où", "donc", "car", "si", "tout", "toute", "entre", "aussi", "peut", "être"
        )
    }
}
