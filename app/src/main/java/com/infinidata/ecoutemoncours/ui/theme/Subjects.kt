package com.infinidata.ecoutemoncours.ui.theme

import androidx.compose.ui.graphics.Color

/** Pastille et couleur par matiere : la bibliotheque se lit d'un coup d'oeil. */
object Subjects {

    private val MAP = mapOf(
        "Mathématiques" to ("📐" to Color(0xFF7C5CFF)),
        "Physique-Chimie" to ("⚗️" to Color(0xFF00B3FF)),
        "SVT" to ("🧬" to Color(0xFF00D4C8)),
        "Histoire-Géo" to ("🏛️" to Color(0xFFFF7A5C)),
        "Français" to ("📖" to Color(0xFFFFC46B)),
        "Anglais" to ("🇬🇧" to Color(0xFF5C9BFF)),
        "SES" to ("📊" to Color(0xFF9BE04A)),
        "Philosophie" to ("🦉" to Color(0xFFC08CFF))
    )

    fun emoji(subject: String): String = MAP[subject]?.first ?: "📓"

    fun color(subject: String): Color = MAP[subject]?.second ?: Color(0xFF8A939F)
}
