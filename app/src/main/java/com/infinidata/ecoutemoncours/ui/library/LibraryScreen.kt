package com.infinidata.ecoutemoncours.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.infinidata.ecoutemoncours.data.db.DocumentEntity
import com.infinidata.ecoutemoncours.ui.MainViewModel
import com.infinidata.ecoutemoncours.ui.theme.LocalSkin
import com.infinidata.ecoutemoncours.ui.theme.Skins
import com.infinidata.ecoutemoncours.ui.theme.SkinController
import com.infinidata.ecoutemoncours.ui.theme.Subjects

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onWrite: () -> Unit,
    onDictate: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val documents by vm.documents.collectAsState()
    val importState by vm.importState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val skin = LocalSkin.current

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.mapNotNull { it.imageUri }.orEmpty()
            if (pages.isNotEmpty()) vm.importScannedPages(pages)
        }
    }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.importUri(it) } }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importUri(it) } }

    LaunchedEffect(importState.lastDocId) {
        importState.lastDocId?.let { id -> vm.clearImport(); onOpen(id) }
    }
    LaunchedEffect(importState.message) {
        importState.message?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
        ) {
            item {
                Greeting(
                    documents = documents,
                    name = vm.settings.profileName,
                    avatar = vm.settings.avatarEmoji,
                    onProfile = onProfile,
                    onSettings = onSettings,
                    onSearch = onSearch
                )
            }
            item { SkinRow(vm) }

            if (importState.busy) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = skin.accent
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            importState.step,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                ImportTiles(
                    onScan = {
                        val options = GmsDocumentScannerOptions.Builder()
                            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                            .setGalleryImportAllowed(true)
                            .setPageLimit(20)
                            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                            .build()
                        activity?.let { act ->
                            GmsDocumentScanning.getClient(options).getStartScanIntent(act)
                                .addOnSuccessListener { sender ->
                                    scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                }
                        }
                    },
                    onPhoto = {
                        photoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onFile = {
                        fileLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain"
                            )
                        )
                    },
                    onSearch = onSearch,
                    onWrite = onWrite,
                    onDictate = onDictate
                )
            }

            if (documents.isEmpty() && !importState.busy) {
                item { EmptyState() }
            } else {
                item {
                    Text(
                        "CONTINUER",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 26.dp, bottom = 12.dp)
                    )
                }
                items(documents, key = { it.id }) { doc ->
                    CourseCard(doc, onOpen = { onOpen(doc.id) }, onDelete = { vm.delete(doc.id) })
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun Greeting(
    documents: List<DocumentEntity>,
    name: String,
    avatar: String,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit
) {
    val skin = LocalSkin.current
    val minutes = documents.sumOf { (it.charCount / 900).coerceAtLeast(1) }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isBlank()) "Salut 👋" else "Salut $name 👋",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (documents.isEmpty()) "Prête à transformer un cours en audio ?"
                else "${documents.size} cours · environ $minutes min d'écoute",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, "Chercher un cours", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, "Réglages", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // L'avatar ouvre le profil : prenom, tete, code.
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(skin.gradient).clickable(onClick = onProfile),
            contentAlignment = Alignment.Center
        ) {
            Text(avatar, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SkinRow(vm: MainViewModel) {
    val current = SkinController.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Skins.ALL.forEach { skin ->
            val selected = skin.id == current.id
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (selected) 1.5.dp else 0.dp,
                        color = if (selected) current.accent else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { SkinController.apply(skin) { id -> vm.settings.skinId = id } }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(skin.accent))
                Spacer(Modifier.width(7.dp))
                Text(
                    skin.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImportTiles(
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onFile: () -> Unit,
    onSearch: () -> Unit,
    onWrite: () -> Unit,
    onDictate: () -> Unit
) {
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(skin.gradient)
                .clickable(onClick = onScan).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📸", fontSize = 24.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Scanner mon cours",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    color = skin.onAccent
                )
                Text(
                    "Photographie les pages, même plusieurs d'affilée",
                    style = MaterialTheme.typography.labelSmall,
                    color = skin.onAccent.copy(alpha = 0.72f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTile("🖼️", "Une photo", "Depuis la galerie", Modifier.weight(1f), onPhoto)
            SmallTile("📄", "Un fichier", "PDF, Word, texte", Modifier.weight(1f), onFile)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTile("🎤", "Dicter", "Parle, ça s'écrit", Modifier.weight(1f), onDictate)
            SmallTile("✍️", "Écrire", "Taper ou coller", Modifier.weight(1f), onWrite)
        }
        SmallTile("🔎", "Chercher en ligne", "Par matière et par niveau", Modifier.fillMaxWidth(), onSearch)
    }
}

@Composable
private fun SmallTile(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CourseCard(doc: DocumentEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val color = Subjects.color(doc.subject)
    val progress = if (doc.charCount > 0) (doc.resumeOffset.toFloat() / doc.charCount).coerceIn(0f, 1f) else 0f
    val minutes = (doc.charCount / 900).coerceAtLeast(1)

    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Text(Subjects.emoji(doc.subject), fontSize = 24.sp) }

        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                "${doc.subject} · $minutes min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (progress > 0.01f) ProgressRing(progress, color)

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.DeleteOutline, "Supprimer",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.5.dp.toPx()
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2)
            )
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2)
            )
        }
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
        Text("Rien à écouter pour l'instant", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scanne une page, choisis une photo ou importe un PDF. " +
                "Le cours se met à parler, le mot lu se surligne, et tu reprends toujours " +
                "là où tu t'étais arrêtée.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
