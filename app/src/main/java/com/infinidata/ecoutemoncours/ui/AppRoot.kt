package com.infinidata.ecoutemoncours.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infinidata.ecoutemoncours.ui.dictation.DictationScreen
import com.infinidata.ecoutemoncours.ui.editor.EditorScreen
import com.infinidata.ecoutemoncours.ui.library.LibraryScreen
import com.infinidata.ecoutemoncours.ui.profile.LockScreen
import com.infinidata.ecoutemoncours.ui.profile.ProfileScreen
import com.infinidata.ecoutemoncours.ui.reader.ReaderScreen
import com.infinidata.ecoutemoncours.ui.search.SearchScreen
import com.infinidata.ecoutemoncours.ui.settings.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val EDITOR = "editor"
    const val PROFILE = "profile"
    const val DICTATION = "dictation"
}

@Composable
fun AppRoot(sharedUri: Uri?, onSharedConsumed: () -> Unit) {
    val navController = rememberNavController()
    val vm: MainViewModel = viewModel()

    // Premier lancement : on demande le prenom et l'avatar.
    var profileReady by rememberSaveable { mutableStateOf(vm.settings.isProfileReady) }
    // Verrou local facultatif.
    // rememberSaveable : une rotation d'ecran ne doit pas redemander le code.
    var unlocked by rememberSaveable { mutableStateOf(!vm.settings.hasPin) }

    if (!profileReady) {
        ProfileScreen(
            settings = vm.settings,
            firstRun = true,
            onBack = null,
            onDone = { profileReady = true; unlocked = true }
        )
        return
    }
    if (!unlocked) {
        LockScreen(settings = vm.settings, onUnlocked = { unlocked = true })
        return
    }

    LaunchedEffect(sharedUri) {
        sharedUri?.let {
            vm.importUri(it)
            onSharedConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                vm = vm,
                onOpen = { id -> navController.navigate("${Routes.READER}/$id") },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onProfile = { navController.navigate(Routes.PROFILE) },
                onWrite = { navController.navigate("${Routes.EDITOR}/-1") },
                onDictate = { navController.navigate(Routes.DICTATION) }
            )
        }
        composable(
            route = "${Routes.READER}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            ReaderScreen(
                docId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("${Routes.EDITOR}/$id") }
            )
        }
        composable(
            route = "${Routes.EDITOR}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            EditorScreen(
                docId = id,
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.popBackStack()
                    if (id <= 0) navController.navigate("${Routes.READER}/$savedId")
                }
            )
        }
        composable(Routes.DICTATION) {
            DictationScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("${Routes.EDITOR}/$id")
                }
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                settings = vm.settings,
                firstRun = false,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(settings = vm.settings, onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate("${Routes.READER}/$id") }
            )
        }
    }
}
