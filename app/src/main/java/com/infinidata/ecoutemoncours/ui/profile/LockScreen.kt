package com.infinidata.ecoutemoncours.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinidata.ecoutemoncours.data.Settings
import com.infinidata.ecoutemoncours.ui.theme.LocalSkin

/** Verrou local : quatre chiffres, comparés sur le telephone, rien d'autre. */
@Composable
fun LockScreen(settings: Settings, onUnlocked: () -> Unit) {
    val skin = LocalSkin.current
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        if (entry.length == 4) {
            if (entry == settings.pinCode) onUnlocked() else { wrong = true; entry = "" }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(skin.gradient),
            contentAlignment = Alignment.Center
        ) { Text(settings.avatarEmoji, fontSize = 40.sp) }

        Spacer(Modifier.height(18.dp))
        Text(
            "Salut ${settings.profileName}",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (wrong) "Ce n'est pas le bon code" else "Ton code à 4 chiffres",
            style = MaterialTheme.typography.labelSmall,
            color = if (wrong) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { index ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(
                            if (index < entry.length) skin.accent
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(34.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "←")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        Box(
                            Modifier.size(72.dp).clip(CircleShape)
                                .background(
                                    if (key.isEmpty()) androidx.compose.ui.graphics.Color.Transparent
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable(enabled = key.isNotEmpty()) {
                                    wrong = false
                                    entry = when (key) {
                                        "←" -> entry.dropLast(1)
                                        else -> (entry + key).take(4)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, fontSize = if (key == "←") 20.sp else 24.sp)
                        }
                    }
                }
            }
        }
    }
}
