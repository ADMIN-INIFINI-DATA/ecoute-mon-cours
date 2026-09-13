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

    data class Imported(
        val text: String,
        val source: String,
        val engine: String,
        val warning: String? = null,
        /** Pages conservees (un chemin par ligne), pour une relecture ulterieure. */
        val imagePaths: String? = null
    )

    /** Client IA configure avec le modele retenu lors du test de connexion. */
    fun geminiClient(settings: Settings): GeminiClient =
        if (settings.aiModel.isNotBlank()) GeminiClient(settings.geminiKey, settings.aiModel)
        else GeminiClient(settings.geminiKey)

    /** Copie l'image dans le stockage prive de l'application : la photo d'origine
     *  peut disparaitre (cache vide, permission expiree), la relecture IA non. */
    suspend fun keepPage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.filesDir, "pages").apply { mkdirs() }
            val file = java.io.File(dir, "page_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

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
        val kept = keepPage(context, uri)
        val local = runCatching { MlKitOcr.recognizeUri(context, uri) }.getOrNull()

        if (local != null && !local.looksUnreliable) {
            return Imported(TextCleanup.clean(local.text), "scan", "local", imagePaths = kept)
        }

        // Page pale, crayon a papier, photo de tableau : on redresse le contraste
        // et on relit. C'est ce qui sauve la majorite des documents scolaires.
        val enhanced = enhancedPass(context, uri)
        val best = listOfNotNull(local, enhanced)
            .maxByOrNull { ImageEnhancer.score(it.text) }
        if (best != null && !best.looksUnreliable) {
            return Imported(TextCleanup.clean(best.text), "scan", "local", imagePaths = kept)
        }

        // Page manuscrite ou photo difficile : bascule cloud si elle est autorisee.
        if (settings.hasGeminiKey && settings.cloudOcrEnabled) {
            val cloud = runCatching { geminiClient(settings).transcribeImage(context, uri) }
            cloud.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                return Imported(TextCleanup.clean(it), "scan", "cloud", imagePaths = kept)
            }
            return Imported(
                TextCleanup.clean(best?.text.orEmpty()), "scan", "local",
                warning = "La lecture assistée a échoué : " + (cloud.exceptionOrNull()?.message ?: "raison inconnue"),
                imagePaths = kept
            )
        }

        return Imported(
            text = TextCleanup.clean(best?.text.orEmpty()),
            source = "scan",
            engine = "local",
            imagePaths = kept,
            warning = if (best == null || best.looksUnreliable)
                "Peu de texte lisible, même après renforcement du contraste. " +
                    "Si la page est manuscrite ou très pâle, active la lecture assistée dans Réglages."
            else null
        )
    }

    /** Transcription forcee par l'IA, quel que soit le resultat de la lecture locale.
     *  C'est ce que declenche le bouton « Relire avec l'IA » : sur une page manuscrite,
     *  la lecture locale renvoie souvent du charabia credible plutot que rien, et
     *  aucune heuristique ne remplace le jugement de l'utilisatrice. */
    suspend fun forceCloud(context: Context, imagePaths: List<String>, settings: Settings): String {
        require(settings.hasGeminiKey) { "Aucune clé IA n'est enregistrée dans les Réglages." }
        require(imagePaths.isNotEmpty()) { "Les images d'origine ne sont plus disponibles : refais un scan." }
        val client = geminiClient(settings)
        val sb = StringBuilder()
        var missing = 0
        imagePaths.forEach { path ->
            val file = java.io.File(path)
            if (file.exists()) {
                sb.append(client.transcribeImage(context, Uri.fromFile(file))).append("\n\n")
            } else {
                missing++
            }
        }
        val text = TextCleanup.clean(sb.toString())
        require(text.isNotBlank()) { "Le service IA n'a rien pu lire sur ces pages." }
        // Une transcription partielle ne doit pas passer pour complete.
        return if (missing > 0) {
            "$text\n\n[$missing page(s) d'origine introuvable(s) : elles n'ont pas été relues.]"
        } else text
    }

    /** Relecture de l'image apres passage en noir et blanc a seuil adaptatif. */
    private suspend fun enhancedPass(context: Context, uri: Uri): OcrResult? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageEnhancer.load(context, uri) ?: return@runCatching null
            val cleaned = ImageEnhancer.enhance(source)
            source.recycle()
            val result = MlKitOcr.recognize(cleaned)
            cleaned.recycle()
            result
        }.getOrNull()
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
            ocrEngine = imported.engine,
            imagePaths = imported.imagePaths
        )
    }
}
