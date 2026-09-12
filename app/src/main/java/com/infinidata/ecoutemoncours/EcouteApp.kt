package com.infinidata.ecoutemoncours

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.infinidata.ecoutemoncours.data.Settings
import com.infinidata.ecoutemoncours.data.db.AppDatabase
import com.infinidata.ecoutemoncours.data.db.DocumentDao
import com.infinidata.ecoutemoncours.speech.SpeechController
import com.infinidata.ecoutemoncours.ui.theme.SkinController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EcouteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYBACK,
                getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )

        // La progression doit continuer d'etre enregistree quand l'ecran de lecture est ferme
        // et que seule la notification pilote la lecture : le scope est donc applicatif.
        SpeechController.onProgressPersist = { docId, offset ->
            appScope.launch { dao.saveProgress(docId, offset) }
        }
        // Le Keystore materiel met quelques centaines de millisecondes a produire la cle
        // au tout premier lancement : on ne le fait pas sur le thread d'interface.
        appScope.launch { settings.speechRate }
        // Le style choisi est restaure avant le premier affichage, depuis des reglages
        // en clair : aucun acces au Keystore sur le thread principal.
        runCatching { SkinController.restore(Settings.readSkinId(this)) }
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: Settings by lazy { Settings(this) }
    val dao: DocumentDao by lazy { AppDatabase.get(this).documentDao() }

    companion object {
        const val CHANNEL_PLAYBACK = "playback"
        lateinit var instance: EcouteApp
            private set
    }
}
