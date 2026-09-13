package com.infinidata.ecoutemoncours.ui.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinidata.ecoutemoncours.ui.theme.LocalSkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(docId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm: ReaderViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val speech by vm.speech.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val skin = LocalSkin.current
    var showSheet by remember { mutableStateOf(false) }
    var showVoiceOptions by remember { mutableStateOf(false) }

    LaunchedEffect(docId) { vm.load(docId) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri -> uri?.let { vm.export(it) } }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(speech.error) { speech.error?.let { snackbar.showSnackbar(it) } }

    // Suivi automatique : le paragraphe en cours de lecture reste a l'ecran.
    val currentParagraph = remember(speech.offset, ui.paragraphs) {
        ui.paragraphs.indexOfLast { it.first <= speech.offset }
    }
    LaunchedEffect(currentParagraph) {
        if (speech.isPlaying && currentParagraph >= 0) {
            listState.animateScrollToItem(currentParagraph)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(ui.doc?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    IconButton(
                        onClick = { vm.reReadWithAi() },
                        enabled = ui.busyMessage == null
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Relire avec l'IA")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Corriger le texte")
                    }
                    IconButton(onClick = { showSheet = true }) {
                        Icon(Icons.Default.Summarize, contentDescription = "Fiche de révision")
                    }
                    IconButton(onClick = {
                        val safeName = (ui.doc?.title ?: "cours")
                            .replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(40).trim()
                        exportLauncher.launch("$safeName.m4a")
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Exporter l'audio")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            PlayerBar(
                isPlaying = speech.isPlaying,
                position = speech.segmentIndex + 1,
                total = speech.segmentCount,
                rate = vm.settings.speechRate,
                onToggle = { vm.toggle() },
                onNext = { vm.next() },
                onPrevious = { vm.previous() },
                onRate = { vm.setRate(it) },
                onVoices = { showVoiceOptions = true }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            ui.busyMessage?.let { msg ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(ui.paragraphs) { _, (offset, line) ->
                    ParagraphText(
                        highlightColor = skin.highlight,
                        text = line,
                        paragraphStart = offset,
                        highlightStart = speech.highlightStart,
                        highlightEnd = speech.highlightEnd,
                        highlightEnabled = vm.settings.highlightEnabled,
                        extraSpacing = vm.settings.dyslexiaSpacing,
                        fontScale = vm.settings.fontScale,
                        onClick = { vm.seek(offset) }
                    )
                }
                item {
                    ui.doc?.attribution?.let {
                        Text(
                            "Source : $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 18.dp)
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()).heightIn(max = 520.dp)
            ) {
                Text("Fiche de révision", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                if (ui.sheet.isNullOrBlank()) {
                    Text(
                        "Génère une fiche à partir du cours : l'essentiel, les définitions, " +
                            "ce qu'il faut retenir par cœur et des questions pour se tester.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.buildRevisionSheet() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Créer la fiche") }
                } else {
                    Text(ui.sheet.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.playSheet(); showSheet = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Écouter la fiche") }
                        OutlinedButton(onClick = { vm.buildRevisionSheet() }) { Text("Regénérer") }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showVoiceOptions) {
        VoiceDialog(vm = vm, onDismiss = { showVoiceOptions = false })
    }
}

@Composable
private fun ParagraphText(
    highlightColor: androidx.compose.ui.graphics.Color,
    text: String,
    paragraphStart: Int,
    highlightStart: Int,
    highlightEnd: Int,
    highlightEnabled: Boolean,
    extraSpacing: Boolean,
    fontScale: Float,
    onClick: () -> Unit
) {
    val end = paragraphStart + text.length
    val annotated: AnnotatedString = remember(text, paragraphStart, highlightStart, highlightEnd, highlightEnabled) {
        if (!highlightEnabled || highlightStart < 0 || highlightEnd <= paragraphStart || highlightStart >= end) {
            AnnotatedString(text)
        } else {
            val from = (highlightStart - paragraphStart).coerceIn(0, text.length)
            val to = (highlightEnd - paragraphStart).coerceIn(from, text.length)
            buildAnnotatedString {
                append(text.substring(0, from))
                withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(from, to))
                }
                append(text.substring(to))
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (17 * fontScale).sp,
            lineHeight = (if (extraSpacing) 38 else 28).sp,
            letterSpacing = if (extraSpacing) 0.8.sp else 0.sp
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
private fun PlayerBar(
    isPlaying: Boolean,
    position: Int,
    total: Int,
    rate: Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRate: (Float) -> Unit,
    onVoices: () -> Unit
) {
    var currentRate by remember { mutableFloatStateOf(rate) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vitesse ${"%.1f".format(currentRate)}×",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = currentRate,
                    onValueChange = { currentRate = it },
                    onValueChangeFinished = { onRate(currentRate) },
                    valueRange = 0.6f..2.5f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                IconButton(onClick = onVoices) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = "Voix", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Passage précédent")
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(32.dp))
                        .background(LocalSkin.current.gradient).clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Passage suivant")
                }
            }
            if (total > 0) {
                Text(
                    "Passage $position sur $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun VoiceDialog(vm: ReaderViewModel, onDismiss: () -> Unit) {
    val voices = remember { com.infinidata.ecoutemoncours.speech.SpeechController.availableVoices() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Voix française") },
        text = {
            if (voices.isEmpty()) {
                Text("Aucune voix française hors-ligne n'est installée. " +
                    "Ouvre Réglages Android > Langues et saisie > Synthèse vocale pour en télécharger une.")
            } else {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    voices.forEach { voice ->
                        Text(
                            voice.name,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    com.infinidata.ecoutemoncours.speech.SpeechController.selectVoice(voice)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
