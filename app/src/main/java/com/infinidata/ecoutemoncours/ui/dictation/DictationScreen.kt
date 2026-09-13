package com.infinidata.ecoutemoncours.ui.dictation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinidata.ecoutemoncours.ui.theme.LocalSkin

/**
 * Dictee d'un cours. Le texte s'ecrit pendant qu'elle parle, puis l'ecran d'edition
 * s'ouvre pour la correction — parler vite et corriger ensuite bat toujours la saisie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: DictationViewModel = viewModel()
    val state by vm.state.collectAsState()
    val savedId by vm.savedId.collectAsState()
    val skin = LocalSkin.current
    val snackbar = remember { SnackbarHostState() }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val recognitionAvailable = remember { vm.dictation.available() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    // Micro coupe des que l'ecran passe en arriere-plan : on ne laisse pas une
    // application d'ecoute tourner sans que l'utilisatrice le voie.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.dictation.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(savedId) { savedId?.let(onSaved) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }

    val pulse by animateFloatAsState(
        targetValue = if (state.listening) 1f + state.level * 0.18f else 1f,
        label = "pulse"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Dicter un cours") },
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
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(84.dp).scale(pulse).clip(CircleShape)
                            .background(if (state.listening) skin.gradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant))
                            .clickable(enabled = granted && recognitionAvailable) { vm.toggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.listening) Icons.Default.Pause else Icons.Default.Mic,
                            contentDescription = if (state.listening) "Pause" else "Parler",
                            tint = if (state.listening) skin.onAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            !recognitionAvailable ->
                                "Ce téléphone n'a pas de moteur de reconnaissance vocale."
                            !granted -> "Autorise le micro pour dicter"
                            state.listening && state.onDevice -> "J'écoute — sans connexion"
                            state.listening -> "J'écoute…"
                            state.text.isBlank() -> "Appuie et parle"
                            else -> "En pause — appuie pour continuer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.text.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { vm.dictation.clear() }) { Text("Effacer") }
                            Button(
                                onClick = { vm.save() },
                                colors = ButtonDefaults.buttonColors(containerColor = skin.accent)
                            ) { Text("Enregistrer et corriger") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            if (state.text.isBlank() && state.partial.isBlank()) {
                Spacer(Modifier.height(20.dp))
                Text("Comment ça marche", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Appuie sur le micro et récite ta fiche à voix haute. Le texte s'écrit au fur " +
                        "et à mesure ; dis « point » ou « à la ligne » pour la ponctuation. " +
                        "Quand tu as fini, tu relis et tu corriges — la reconnaissance se trompe " +
                        "toujours un peu sur les mots de cours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))
            if (state.text.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (state.partial.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.partial,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
