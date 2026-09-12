package com.infinidata.ecoutemoncours.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Cinq apparences au choix, changeables a tout moment depuis l'application.
 * Le style choisi est enregistre : c'est son application, pas un theme impose.
 */
data class Skin(
    val id: String,
    val label: String,
    val accent: Color,
    val accent2: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val secondary: Color,
    val highlight: Color,
    val onAccent: Color,
    val isLight: Boolean = false
) {
    val gradient: Brush = Brush.linearGradient(listOf(accent, accent2))
}

object Skins {

    val NEON = Skin(
        id = "neon", label = "Néon",
        accent = Color(0xFF7C5CFF), accent2 = Color(0xFF00D4C8),
        background = Color(0xFF0F1115), surface = Color(0xFF181B22), surfaceVariant = Color(0xFF20242D),
        onBackground = Color(0xFFF4F6FA), secondary = Color(0xFF9AA3B2),
        highlight = Color(0x737C5CFF), onAccent = Color(0xFF0B0D12)
    )

    val FLUO = Skin(
        id = "fluo", label = "Fluo",
        accent = Color(0xFF8CE04A), accent2 = Color(0xFF4CA22F),
        background = Color(0xFF0B0D0C), surface = Color(0xFF161A17), surfaceVariant = Color(0xFF1E241F),
        onBackground = Color(0xFFF2F6F2), secondary = Color(0xFF95A197),
        highlight = Color(0x598CE04A), onAccent = Color(0xFF08120A)
    )

    val SUNSET = Skin(
        id = "sunset", label = "Coucher",
        accent = Color(0xFFFF7A5C), accent2 = Color(0xFFFFC46B),
        background = Color(0xFF14100F), surface = Color(0xFF1F1917), surfaceVariant = Color(0xFF2A2220),
        onBackground = Color(0xFFFDF3EE), secondary = Color(0xFFB3A29A),
        highlight = Color(0x61FF7A5C), onAccent = Color(0xFF1A0D08)
    )

    val PAPIER = Skin(
        id = "papier", label = "Papier",
        accent = Color(0xFF2E7D64), accent2 = Color(0xFF7ED0A8),
        background = Color(0xFFF6F5F2), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEFEDE8),
        onBackground = Color(0xFF1B1D22), secondary = Color(0xFF5A606C),
        highlight = Color(0x8C7ED0A8), onAccent = Color(0xFF0C2A20), isLight = true
    )

    val SOBRE = Skin(
        id = "sobre", label = "Sobre",
        accent = Color(0xFF4CA22F), accent2 = Color(0xFF3A7D24),
        background = Color(0xFF1C1F26), surface = Color(0xFF252932), surfaceVariant = Color(0xFF2B303A),
        onBackground = Color(0xFFF2F4F7), secondary = Color(0xFF8A939F),
        highlight = Color(0x664CA22F), onAccent = Color(0xFF06120A)
    )

    val ALL = listOf(NEON, FLUO, SUNSET, PAPIER, SOBRE)

    fun byId(id: String): Skin = ALL.firstOrNull { it.id == id } ?: NEON
}

/** Apparence courante, partagee par tous les ecrans. */
object SkinController {
    var current by mutableStateOf(Skins.NEON)
        private set

    /** Applique un style ; [persist] enregistre le choix dans les reglages. */
    fun apply(skin: Skin, persist: (String) -> Unit = {}) {
        current = skin
        persist(skin.id)
    }

    fun restore(id: String) { current = Skins.byId(id) }
}

val LocalSkin = staticCompositionLocalOf { Skins.NEON }

private fun schemeOf(skin: Skin) = if (skin.isLight) {
    lightColorScheme(
        primary = skin.accent, onPrimary = skin.onAccent,
        primaryContainer = skin.accent2, onPrimaryContainer = skin.onBackground,
        secondary = skin.secondary, onSecondary = skin.background,
        background = skin.background, onBackground = skin.onBackground,
        surface = skin.surface, onSurface = skin.onBackground,
        surfaceVariant = skin.surfaceVariant, onSurfaceVariant = skin.secondary,
        secondaryContainer = skin.surfaceVariant, onSecondaryContainer = skin.onBackground,
        tertiary = skin.accent2, onTertiary = skin.onAccent,
        inverseSurface = skin.onBackground, inverseOnSurface = skin.background,
        outlineVariant = skin.surfaceVariant,
        outline = Color(0xFFCFCDC7), error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF)
    )
} else {
    darkColorScheme(
        primary = skin.accent, onPrimary = skin.onAccent,
        primaryContainer = skin.accent2, onPrimaryContainer = skin.onBackground,
        secondary = skin.secondary, onSecondary = skin.background,
        background = skin.background, onBackground = skin.onBackground,
        surface = skin.surface, onSurface = skin.onBackground,
        surfaceVariant = skin.surfaceVariant, onSurfaceVariant = skin.secondary,
        secondaryContainer = skin.surfaceVariant, onSecondaryContainer = skin.onBackground,
        tertiary = skin.accent2, onTertiary = skin.onAccent,
        inverseSurface = skin.onBackground, inverseOnSurface = skin.background,
        outlineVariant = skin.surfaceVariant,
        outline = Color(0xFF3C424E), error = Color(0xFFE2725B), onError = skin.background
    )
}

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 31.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun EcouteTheme(content: @Composable () -> Unit) {
    val skin = SkinController.current
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(colorScheme = schemeOf(skin), typography = AppTypography, content = content)
    }
}
