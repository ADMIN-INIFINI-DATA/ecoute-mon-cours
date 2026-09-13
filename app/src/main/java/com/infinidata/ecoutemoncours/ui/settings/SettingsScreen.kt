package com.infinidata.ecoutemoncours.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinidata.ecoutemoncours.data.Settings
import com.infinidata.ecoutemoncours.ui.theme.SkinController
import com.infinidata.ecoutemoncours.ui.theme.Skins

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit) {
    val diag: DiagnosticViewModel = viewModel()
    val diagState by diag.state.collectAsState()
    var key by remember { mutableStateOf(settings.geminiKey) }
    var cloudOcr by remember { mutableStateOf(settings.cloudOcrEnabled) }
    var highlight by remember { mutableStateOf(settings.highlightEnabled) }
    var dys by remember { mutableStateOf(settings.dyslexiaSpacing) }
    var fontScale by remember { mutableFloatStateOf(settings.fontScale) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SectionTitle("Apparence")
            Text(
                "Cinq styles, changeables à tout moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skins.ALL.forEach { skin ->
                    val selected = skin.id == SkinController.current.id
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { SkinController.apply(skin) { id -> settings.skinId = id } }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(skin.gradient)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(skin.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (selected) {
                            Text("choisi", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            SectionTitle("Lecture")

            SwitchRow("Surligner le mot lu", highlight) {
                highlight = it; settings.highlightEnabled = it
            }
            SwitchRow("Espacement renforcé (lecture facilitée)", dys) {
                dys = it; settings.dyslexiaSpacing = it
            }

            Text("Taille du texte", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = fontScale,
                onValueChange = { fontScale = it },
                onValueChangeFinished = { settings.fontScale = fontScale },
                valueRange = 0.85f..1.6f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
            Text(
                "Exemple de texte à cette taille.",
                fontSize = (17 * fontScale).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("Lecture assistée (pages manuscrites, fiches de révision)")

            Text(
                "Tout fonctionne sans compte ni connexion pour les pages imprimées. " +
                    "Pour lire une page manuscrite ou générer une fiche de révision, l'application a besoin " +
                    "d'une clé Google AI Studio (offre gratuite disponible). " +
                    "La clé est chiffrée sur le téléphone et n'est envoyée qu'à Google.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Clé API Gemini") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { state ->
                        // Enregistrement au moment ou l'on quitte le champ : le Keystore
                        // n'est pas sollicite a chaque caractere tape.
                        if (!state.isFocused) {
                            settings.geminiKey = key.trim()
                            // Saisir une cle vaut activation : sinon la cle ne sert a rien
                            // et l'on croit la fonction en panne.
                            if (key.isNotBlank() && !cloudOcr) {
                                cloudOcr = true
                                settings.cloudOcrEnabled = true
                            }
                        }
                    }
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("Relire automatiquement les pages illisibles avec l'IA", cloudOcr) {
                cloudOcr = it; settings.cloudOcrEnabled = it
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { settings.geminiKey = key.trim(); diag.test() },
                    enabled = !diagState.running && key.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(if (diagState.running) "Test en cours…" else "Tester la connexion IA") }
                if (settings.aiModel.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        settings.aiModel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            diagState.report?.let { report ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        report,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Confidentialité")
            Text(
                "Les cours restent sur le téléphone. Aucune donnée n'est envoyée ailleurs, " +
                    "sauf si la lecture assistée est activée : dans ce cas, seule la page concernée " +
                    "est transmise au service d'IA choisi, avec ta propre clé.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Version " + com.infinidata.ecoutemoncours.BuildConfig.VERSION_NAME +
                    " (build " + com.infinidata.ecoutemoncours.BuildConfig.VERSION_CODE + ")",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        )
    }
}
