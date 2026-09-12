package com.infinidata.ecoutemoncours.ingest

import android.content.Context
import android.net.Uri
import com.infinidata.ecoutemoncours.ai.GeminiClient
import com.infinidata.ecoutemoncours.data.Settings
import com.infinidata.ecoutemoncours.data.db.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aiguillage d'un fichier entrant vers le bon extracteur.
 * Regle de conduite : tout ce qui peut se faire hors-ligne se fait hors-ligne.
 * Le cloud n'est sollicite que si le texte local est manifestement inexploitable
 * (page manuscrite, photo du tableau) ET si l'utilisateur a fourni une cle.
 */
object Importer {

    data class Imported(val text: String, val source: String, val engine: String, val warning: String? = null)

    suspend fun import(context: Context, uri: Uri, settings: Settings): Imported {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when {
            mime.startsWith("image/") -> importImage(context, uri, settings)
            mime == "application/pdf" -> Imported(PdfImporter.extract(context, uri), "pdf", "local")
            mime.contains("wordprocessingml") || mime == "application/msword" ->
                Imported(DocxImporter.extract(context, uri), "docx", "local")
            mime.startsWith("text/") -> Imported(readPlainText(context, uri), "texte", "aucun")
            else -> {
                // Certains gestionnaires de fichiers ne renseignent pas le type MIME.
                val name = uri.lastPathSegment.orEmpty().lowercase()
                when {
                    name.endsWith(".pdf") -> Imported(PdfImporter.extract(context, uri), "pdf", "local")
                    name.endsWith(".docx") -> Imported(DocxImporter.extract(context, uri), "docx", "local")
                    name.endsWith(".txt") || name.endsWith(".md") ->
                        Imported(readPlainText(context, uri), "texte", "aucun")
                    else -> importImage(context, uri, settings)
                }
            }
        }
    }

    private suspend fun importImage(context: Context, uri: Uri, settings: Settings): Imported {
        val local = runCatching { MlKitOcr.recognizeUri(context, uri) }.getOrNull()

        if (local != null && !local.looksUnreliable) {
            return Imported(TextCleanup.clean(local.text), "scan", "local")
        }

        // Page manuscrite ou photo difficile : bascule cloud si elle est autorisee.
        if (settings.cloudOcrEnabled && settings.hasGeminiKey) {
            val cloud = runCatching { GeminiClient(settings.geminiKey).transcribeImage(context, uri) }
            cloud.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                return Imported(TextCleanup.clean(it), "scan", "cloud")
            }
            return Imported(
                TextCleanup.clean(local?.text.orEmpty()), "scan", "local",
                warning = "La lecture assistée a échoué : " + (cloud.exceptionOrNull()?.message ?: "raison inconnue")
            )
        }

        return Imported(
            text = TextCleanup.clean(local?.text.orEmpty()),
            source = "scan",
            engine = "local",
            warning = if (local == null || local.looksUnreliable)
                "Peu de texte lisible sur cette image. Si la page est manuscrite, active la lecture assistée dans Réglages."
            else null
        )
    }

    private suspend fun readPlainText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        }.orEmpty()
        TextCleanup.clean(raw)
    }

    fun toDocument(imported: Imported, fallbackTitle: String = "Cours"): DocumentEntity {
        val text = imported.text
        return DocumentEntity(
            title = if (text.isBlank()) fallbackTitle else TextCleanup.guessTitle(text),
            subject = TextCleanup.guessSubject(text),
            source = imported.source,
            text = text,
            ocrEngine = imported.engine
        )
    }
}
