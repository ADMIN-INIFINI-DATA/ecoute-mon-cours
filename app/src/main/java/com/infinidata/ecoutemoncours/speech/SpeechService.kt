package com.infinidata.ecoutemoncours.speech

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.infinidata.ecoutemoncours.EcouteApp
import com.infinidata.ecoutemoncours.MainActivity
import com.infinidata.ecoutemoncours.R

/**
 * Service de premier plan : c'est lui qui permet de continuer a ecouter
 * ecran eteint, avec les commandes dans la barre de notification.
 * Type mediaPlayback, obligatoire depuis Android 14.
 */
class SpeechService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service relance par le systeme alors qu'aucun cours n'est charge : rien a jouer.
        if (!SpeechController.hasContent()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            SpeechController.pause()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_TOGGLE -> SpeechController.toggle()
            ACTION_NEXT -> SpeechController.next()
            ACTION_PREV -> SpeechController.previous()
        }
        startInForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type) }
    }

    private fun buildNotification(): android.app.Notification {
        val state = SpeechController.state.value
        val content = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val progress = if (state.segmentCount > 0)
            "Passage ${state.segmentIndex + 1} sur ${state.segmentCount}" else ""

        return NotificationCompat.Builder(this, EcouteApp.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.title.ifBlank { getString(R.string.app_name) })
            .setContentText(progress)
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(R.drawable.ic_previous, "Précédent", action(ACTION_PREV))
            .addAction(
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (state.isPlaying) "Pause" else "Lecture",
                action(ACTION_TOGGLE)
            )
            .addAction(R.drawable.ic_next, "Suivant", action(ACTION_NEXT))
            .addAction(R.drawable.ic_stop, "Arrêter", action(ACTION_STOP))
            .build()
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this, name.hashCode(),
        Intent(this, SpeechService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val NOTIF_ID = 42
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_STOP = "stop"

        @Volatile private var instance: SpeechService? = null

        /** Met a jour la notification quand l'etat de lecture change. */
        fun refresh() {
            instance?.startInForeground()
        }
    }
}
