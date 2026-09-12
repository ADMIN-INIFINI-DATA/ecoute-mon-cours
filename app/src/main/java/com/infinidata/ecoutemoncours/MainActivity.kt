package com.infinidata.ecoutemoncours

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.drawable.ColorDrawable
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.infinidata.ecoutemoncours.ui.AppRoot
import androidx.compose.ui.graphics.toArgb
import com.infinidata.ecoutemoncours.ui.theme.EcouteTheme
import com.infinidata.ecoutemoncours.ui.theme.LocalSkin

class MainActivity : ComponentActivity() {

    /** Fichier recu via « Partager vers Écoute mon cours », y compris quand
     *  l'application etait deja ouverte (d'ou onNewIntent). */
    private var sharedUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUri = extractUri(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            EcouteTheme {
                // Icones de la barre d'etat et fond de fenetre suivent le style choisi :
                // sans cela, le theme clair donne des icones blanches sur fond clair.
                val skin = LocalSkin.current
                LaunchedEffect(skin.id) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val bars = if (skin.isLight) SystemBarStyle.light(transparent, transparent)
                    else SystemBarStyle.dark(transparent)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                    window.setBackgroundDrawable(ColorDrawable(skin.background.toArgb()))
                }

                // sharedUri est un etat Compose : sa lecture ici suffit a declencher
                // la recomposition quand un fichier est partage vers l'application.
                AppRoot(
                    sharedUri = sharedUri,
                    onSharedConsumed = {
                        sharedUri = null
                        // On neutralise l'intent : sinon une rotation d'ecran
                        // reimporterait le meme fichier une seconde fois.
                        setIntent(Intent())
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUri(intent)?.let { sharedUri = it }
    }

    private fun extractUri(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } else null
}
