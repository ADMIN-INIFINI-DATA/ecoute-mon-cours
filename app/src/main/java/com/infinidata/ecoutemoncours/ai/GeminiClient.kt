package com.infinidata.ecoutemoncours.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Appels IA optionnels (manuscrit + fiche de revision).
 * BYOK : la cle appartient a l'utilisateur, elle est stockee chiffree sur le telephone.
 * Aucune cle n'est embarquee dans l'APK.
 */
class GeminiClient(private val apiKey: String, private val model: String = DEFAULT_MODEL) {

    suspend fun transcribeImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Image illisible")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val prompt = """
            Transcris fidèlement le texte de cette page de cours en français.
            Règles : ne corrige pas l'orthographe, n'ajoute aucun commentaire, ne résume pas.
            Respecte l'ordre de lecture (colonnes de gauche à droite), conserve les titres et les listes.
            Pour un schéma ou une formule que tu ne peux pas transcrire, écris une ligne
            « [schéma : brève description] » ou énonce la formule en toutes lettres.
            Réponds uniquement par le texte transcrit.
        """.trimIndent()

        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", mime).put("data", b64)
                )
            )
        request(parts)
    }

    suspend fun revisionSheet(text: String, subject: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Tu aides une lycéenne à réviser. Voici le texte d'un cours de $subject.
            Produis une fiche de révision en français, structurée ainsi :

            ## L'essentiel
            5 à 8 points clés, une phrase chacun.

            ## Définitions à connaître
            Les termes du cours avec leur définition courte.

            ## À retenir par cœur
            Dates, formules, chiffres — uniquement ceux présents dans le cours.

            ## Questions pour se tester
            6 questions, puis la réponse de chacune sur la ligne suivante.

            Contraintes : n'invente rien qui ne soit pas dans le cours, phrases courtes,
            pas de LaTeX ni de symboles ésotériques (la fiche sera lue à voix haute).

            --- COURS ---
            ${text.take(24000)}
        """.trimIndent()
        request(JSONArray().put(JSONObject().put("text", prompt)))
    }

    private fun request(parts: JSONArray): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.2).put("maxOutputTokens", 4096)
            )

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val payload = try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(friendlyError(code, err))
            }
        } finally {
            conn.disconnect()
        }

        val candidates = JSONObject(payload).optJSONArray("candidates")
            ?: throw IllegalStateException("Réponse vide du service IA.")
        if (candidates.length() == 0) throw IllegalStateException("Réponse vide du service IA.")
        val partsOut = candidates.getJSONObject(0)
            .optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException("Réponse inattendue du service IA.")
        val sb = StringBuilder()
        for (i in 0 until partsOut.length()) sb.append(partsOut.getJSONObject(i).optString("text"))
        return sb.toString().trim()
    }

    private fun friendlyError(code: Int, body: String): String = when (code) {
        400 -> "Requête refusée par le service IA (clé invalide ou image trop lourde)."
        401, 403 -> "Clé API refusée. Vérifie-la dans Réglages."
        429 -> "Quota IA atteint pour le moment. Réessaie dans quelques minutes."
        in 500..599 -> "Le service IA est indisponible. Réessaie plus tard."
        else -> "Erreur du service IA ($code). ${body.take(160)}"
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.0-flash"
    }
}
