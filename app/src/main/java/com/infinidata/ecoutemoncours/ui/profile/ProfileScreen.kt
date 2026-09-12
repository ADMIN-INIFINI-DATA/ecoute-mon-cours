package com.infinidata.ecoutemoncours.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinidata.ecoutemoncours.data.Settings
import com.infinidata.ecoutemoncours.ui.theme.LocalSkin

private val AVATARS = listOf(
    "🎧", "🦊", "🐨", "🐼", "🦉", "🐧", "🦄", "🐙",
    "🌸", "🌙", "⭐", "🔥", "🎸", "🎨", "📚", "🧃",
    "🍀", "🌊", "🍑", "🧁", "🪐", "🎯", "🧩", "🎬"
)

/**
 * Profil local : prenom, avatar, verrou facultatif.
 * Aucun compte en ligne, aucun mot de passe transmis : tout reste sur le telephone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    settings: Settings,
    firstRun: Boolean,
    onBack: (() -> Unit)?,
    onDone: () -> Unit
) {
    val skin = LocalSkin.current
    var name by remember { mutableStateOf(settings.profileName) }
    var avatar by remember { mutableStateOf(settings.avatarEmoji) }
    var pinEnabled by remember { mutableStateOf(settings.hasPin) }
    var pin by remember { mutableStateOf(settings.pinCode) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!firstRun && onBack != null) {
                TopAppBar(
                    title = { Text("Mon profil") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { padding ->
        // Scrollable : clavier ouvert sur un petit ecran, le bouton doit rester atteignable.
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {

            if (firstRun) {
                Spacer(Modifier.height(28.dp))
                Text("Bienvenue 👋", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dis-moi comment t'appeler et choisis ta tête. " +
                        "Tout reste sur ce téléphone, il n'y a aucun compte à créer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(skin.gradient),
                    contentAlignment = Alignment.Center
                ) { Text(avatar, fontSize = 34.sp) }
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20); error = null },
                    label = { Text("Prénom") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("CHOISIS TON AVATAR", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(AVATARS) { emoji ->
                    val selected = emoji == avatar
                    Box(
                        Modifier.aspectRatio(1f).clip(CircleShape)
                            .background(
                                if (selected) skin.accent.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) skin.accent else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { avatar = emoji },
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 18.sp) }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Code à 4 chiffres", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pour que personne d'autre n'ouvre tes cours. Il reste sur le téléphone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = pinEnabled,
                    onCheckedChange = { pinEnabled = it; if (!it) pin = "" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = skin.accent,
                        checkedTrackColor = skin.accent.copy(alpha = 0.4f)
                    )
                )
            }

            if (pinEnabled) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); error = null },
                    label = { Text("Code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(26.dp))
            Button(
                onClick = {
                    when {
                        name.isBlank() -> error = "Il me faut au moins un prénom."
                        pinEnabled && pin.length != 4 -> error = "Le code doit faire exactement 4 chiffres."
                        else -> {
                            settings.profileName = name
                            settings.avatarEmoji = avatar
                            settings.pinCode = if (pinEnabled) pin else ""
                            onDone()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = skin.accent),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (firstRun) "C'est parti" else "Enregistrer", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(40.dp))
        }
    }
}
