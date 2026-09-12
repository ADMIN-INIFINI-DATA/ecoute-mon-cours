package com.infinidata.ecoutemoncours.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinidata.ecoutemoncours.ui.theme.LocalSkin
import com.infinidata.ecoutemoncours.ui.theme.Subjects

private val SUBJECTS = listOf(
    "Mathématiques", "Physique-Chimie", "SVT", "Histoire-Géo",
    "Français", "Anglais", "SES", "Philosophie", "Divers"
)

/**
 * Ecriture et correction d'un cours.
 * Deux usages : saisir ou coller un cours de zero, et corriger le texte issu de la
 * reconnaissance — un mot mal lu s'entend immediatement a l'oral, autant le reparer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(docId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm: EditorViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val skin = LocalSkin.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(docId) { vm.load(docId) }
    LaunchedEffect(ui.savedId) { ui.savedId?.let { onSaved(it) } }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (docId > 0) "Corriger le cours" else "Écrire un cours") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val minutes = if (ui.text.isBlank()) 0 else (ui.text.length / 900).coerceAtLeast(1)
                    Text(
                        "${ui.text.length} caractères · $minutes min d'écoute",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { vm.save() },
                        enabled = ui.text.isNotBlank() && !ui.saving,
                        colors = ButtonDefaults.buttonColors(containerColor = skin.accent)
                    ) { Text(if (docId > 0) "Enregistrer" else "Écouter") }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            OutlinedTextField(
                value = ui.title,
                onValueChange = vm::onTitle,
                label = { Text("Titre du cours") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "MATIÈRE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUBJECTS.forEach { subject ->
                    val selected = subject == ui.subject
                    Row(
                        Modifier.clip(CircleShape)
                            .background(
                                if (selected) Subjects.color(subject).copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { vm.onSubject(subject) }
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Subjects.emoji(subject), fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            subject,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = ui.text,
                onValueChange = vm::onText,
                label = { Text("Texte du cours") },
                placeholder = { Text("Écris ou colle ton cours ici…") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Une phrase par idée et des points bien placés, et la lecture respire tout de suite mieux. " +
                    "C'est aussi ici qu'on répare les mots que le scan a mal lus.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(34.dp))
        }
    }
}
