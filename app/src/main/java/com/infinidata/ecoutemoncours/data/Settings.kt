package com.infinidata.ecoutemoncours.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Reglages + cle API. La cle est chiffree par le Keystore materiel du telephone :
 * elle n'est jamais dans l'APK, jamais envoyee ailleurs qu'au fournisseur choisi.
 */
class Settings(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ecoute_secure_prefs",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as SharedPreferences
    }.getOrElse {
        // Si le Keystore est indisponible (rare, ROM exotique), on degrade proprement.
        context.getSharedPreferences("ecoute_prefs", Context.MODE_PRIVATE)
    }

    var geminiKey: String
        get() = prefs.getString(KEY_GEMINI, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_GEMINI, v).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_RATE, v).apply()

    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_PITCH, v).apply()

    var voiceName: String
        get() = prefs.getString(KEY_VOICE, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_VOICE, v).apply()

    var highlightEnabled: Boolean
        get() = prefs.getBoolean(KEY_HL, true)
        set(v) = prefs.edit().putBoolean(KEY_HL, v).apply()

    var dyslexiaSpacing: Boolean
        get() = prefs.getBoolean(KEY_DYS, false)
        set(v) = prefs.edit().putBoolean(KEY_DYS, v).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_FONT, v).apply()

    /** Identifiant de l'apparence choisie (voir ui.theme.Skins).
     *  Stocke en clair : ce n'est pas un secret, et cela permet de restaurer le style
     *  avant le premier affichage sans reveiller le Keystore materiel. */
    var skinId: String
        get() = readSkinId(appContext)
        set(v) = plainPrefs(appContext).edit().putString(KEY_SKIN, v).apply()

    /** Profil local : prenom et avatar. Stockes en clair, ce ne sont pas des secrets. */
    var profileName: String
        get() = plainPrefs(appContext).getString(KEY_NAME, "").orEmpty()
        set(v) = plainPrefs(appContext).edit().putString(KEY_NAME, v.trim()).apply()

    var avatarEmoji: String
        get() = plainPrefs(appContext).getString(KEY_AVATAR, "🎧").orEmpty().ifBlank { "🎧" }
        set(v) = plainPrefs(appContext).edit().putString(KEY_AVATAR, v).apply()

    /** Code a 4 chiffres facultatif, chiffre par le Keystore. Verrou local uniquement :
     *  il n'ouvre aucun compte et ne quitte jamais le telephone. */
    var pinCode: String
        get() = prefs.getString(KEY_PIN, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_PIN, v).apply()

    val hasPin: Boolean get() = pinCode.length == 4

    val isProfileReady: Boolean get() = profileName.isNotBlank()

    var cloudOcrEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_OCR, false)
        set(v) = prefs.edit().putBoolean(KEY_CLOUD_OCR, v).apply()

    val hasGeminiKey: Boolean get() = geminiKey.isNotBlank()

    companion object {

        private fun plainPrefs(context: Context) =
            context.getSharedPreferences("ecoute_prefs_plain", Context.MODE_PRIVATE)

        /** Lecture directe, utilisable avant toute instanciation de Settings. */
        fun readSkinId(context: Context): String =
            plainPrefs(context).getString(KEY_SKIN, "neon").orEmpty()

        private const val KEY_GEMINI = "gemini_key"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_VOICE = "voice"
        private const val KEY_HL = "highlight"
        private const val KEY_DYS = "dys_spacing"
        private const val KEY_FONT = "font_scale"
        private const val KEY_CLOUD_OCR = "cloud_ocr"
        private const val KEY_SKIN = "skin"
        private const val KEY_NAME = "profile_name"
        private const val KEY_AVATAR = "profile_avatar"
        private const val KEY_PIN = "pin_code"
    }
}
