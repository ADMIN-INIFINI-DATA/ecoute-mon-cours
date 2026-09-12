package com.infinidata.ecoutemoncours.ingest

/**
 * Nettoyage du texte brut avant lecture vocale.
 * C'est ici que se joue la difference entre "ca lit le cours" et "ca lit du charabia" :
 * cesures, entetes/pieds de page, numeros de page, puces, references.
 */
object TextCleanup {

    private val pageNumber = Regex("^\\s*(page\\s*)?\\d{1,3}\\s*(/\\s*\\d{1,3})?\\s*$", RegexOption.IGNORE_CASE)
    private val bulletChar = Regex("^[\\u2022\\u25CF\\u25AA\\u2023\\u2043\\-\\*\\u2219]\\s*")
    private val multiSpace = Regex("[ \\t\\u00A0]{2,}")
    private val multiBreak = Regex("\\n{3,}")

    fun clean(raw: String): String {
        val lines = raw.replace("\r\n", "\n").split("\n")

        // 1. Detection des lignes repetees a l'identique sur plusieurs pages = entete/pied.
        val counts = HashMap<String, Int>()
        lines.forEach { l ->
            val k = l.trim()
            if (k.length in 3..80) counts[k] = (counts[k] ?: 0) + 1
        }
        val repeated = counts.filter { it.value >= 3 }.keys

        val kept = ArrayList<String>(lines.size)
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty()) { kept.add(""); continue }
            if (pageNumber.matches(t)) continue
            if (t in repeated && t.length < 60) continue
            kept.add(bulletChar.replace(t, "• "))
        }

        // 2. Recollage des mots coupes en fin de ligne ("consti-\ntution").
        val sb = StringBuilder()
        var i = 0
        while (i < kept.size) {
            val line = kept[i]
            if (line.endsWith("-") && i + 1 < kept.size && kept[i + 1].isNotEmpty() &&
                kept[i + 1].first().isLowerCase()
            ) {
                sb.append(line.dropLast(1))
                i++
                continue
            }
            sb.append(line)
            // Une ligne qui ne se termine pas par une ponctuation forte continue la phrase.
            val endsSentence = line.isEmpty() || line.last() in ".!?:;»”"
            sb.append(if (endsSentence) "\n" else " ")
            i++
        }

        return sb.toString()
            .replace(multiSpace, " ")
            .replace(multiBreak, "\n\n")
            .replace("œ", "oe")
            .trim()
    }

    /** Titre propose a partir des premieres lignes utiles du document. */
    fun guessTitle(text: String): String {
        val candidate = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.length in 4..70 }
            ?: "Cours sans titre"
        return candidate.take(70)
    }

    /** Matiere devinee a partir de mots-cles courants. Simple mais utile au rangement. */
    fun guessSubject(text: String): String {
        val t = text.lowercase().take(4000)
        val rules = listOf(
            "Mathématiques" to listOf("théorème", "équation", "fonction affine", "dérivée", "vecteur", "probabilité"),
            "Physique-Chimie" to listOf("atome", "molécule", "force", "énergie cinétique", "réaction chimique", "mole"),
            "SVT" to listOf("cellule", "adn", "écosystème", "gène", "photosynthèse", "espèce"),
            "Histoire-Géo" to listOf("révolution", "empire", "traité", "guerre mondiale", "territoire", "urbanisation"),
            "Français" to listOf("figure de style", "registre", "narrateur", "strophe", "dissertation", "commentaire"),
            "Anglais" to listOf("present perfect", "vocabulary", "grammar"),
            "SES" to listOf("pib", "croissance économique", "chômage", "inflation", "marché du travail"),
            "Philosophie" to listOf("conscience", "métaphysique", "kant", "autrui", "liberté")
        )
        return rules.firstOrNull { (_, kws) -> kws.any { it in t } }?.first ?: "Divers"
    }
}
