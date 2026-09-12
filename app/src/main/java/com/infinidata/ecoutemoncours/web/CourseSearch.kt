package com.infinidata.ecoutemoncours.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Recherche de cours en ligne, sur des corpus librement reutilisables uniquement :
 *  - Wikiversite (lecons structurees par niveau)  — CC BY-SA
 *  - Wikipedia     (couverture maximale)          — CC BY-SA
 *  - Vikidia       (college, langage accessible)  — CC BY-SA
 * Les sites scolaires payants (Kartable, SchoolMouv...) ne sont volontairement pas
 * aspires : leurs CGU l'interdisent. Ils restent accessibles par simple lien.
 */
object CourseSearch {

    data class Hit(
        val title: String,
        val snippet: String,
        val source: String,
        val pageUrl: String,
        val apiHost: String,
        val attribution: String
    )

    private val SOURCES = listOf(
        Triple("Wikiversité", "fr.wikiversity.org", "Wikiversité — CC BY-SA 4.0"),
        Triple("Wikipédia", "fr.wikipedia.org", "Wikipédia — CC BY-SA 4.0"),
        Triple("Vikidia", "fr.vikidia.org", "Vikidia — CC BY-SA 3.0")
    )

    suspend fun search(query: String, level: String): List<Hit> = withContext(Dispatchers.IO) {
        val q = listOf(query, level).filter { it.isNotBlank() }.joinToString(" ")
        val out = ArrayList<Hit>()
        for ((label, host, licence) in SOURCES) {
            runCatching {
                val url = "https://$host/w/api.php?action=query&list=search&format=json" +
                        "&srlimit=5&srsearch=${URLEncoder.encode(q, "UTF-8")}"
                val json = JSONObject(get(url))
                val results = json.optJSONObject("query")?.optJSONArray("search") ?: return@runCatching
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val title = item.optString("title")
                    out.add(
                        Hit(
                            title = title,
                            snippet = item.optString("snippet").replace(Regex("<[^>]+>"), ""),
                            source = label,
                            pageUrl = "https://$host/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8"),
                            apiHost = host,
                            attribution = licence
                        )
                    )
                }
            }
        }
        out
    }

    /** Texte brut de l'article, pret pour la synthese vocale. */
    suspend fun fetchPlainText(hit: Hit): String = withContext(Dispatchers.IO) {
        val url = "https://${hit.apiHost}/w/api.php?action=query&prop=extracts&explaintext=1" +
                "&format=json&redirects=1&titles=${URLEncoder.encode(hit.title, "UTF-8")}"
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONObject("pages")
            ?: return@withContext ""
        val first = pages.keys().asSequence().firstOrNull() ?: return@withContext ""
        val extract = pages.getJSONObject(first).optString("extract")
        // On coupe les sections de fin, sans interet a l'oral.
        val cut = listOf("\n== Voir aussi", "\n== Notes et références", "\n== Références", "\n== Liens externes", "\n== Bibliographie")
            .mapNotNull { marker -> extract.indexOf(marker).takeIf { it > 0 } }
            .minOrNull() ?: extract.length
        extract.substring(0, cut).trim()
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            // Les API Wikimedia demandent un User-Agent identifiable.
            setRequestProperty("User-Agent", "EcouteMonCours/1.0 (application scolaire personnelle)")
        }
        try {
            conn.inputStream.use { return it.bufferedReader().readText() }
        } finally {
            conn.disconnect()
        }
    }
}
