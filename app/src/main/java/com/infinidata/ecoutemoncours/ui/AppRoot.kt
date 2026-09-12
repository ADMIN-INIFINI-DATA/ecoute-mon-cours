package com.infinidata.ecoutemoncours.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infinidata.ecoutemoncours.ui.library.LibraryScreen
import com.infinidata.ecoutemoncours.ui.reader.ReaderScreen
import com.infinidata.ecoutemoncours.ui.search.SearchScreen
import com.infinidata.ecoutemoncours.ui.settings.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
}

@Composable
fun AppRoot(sharedUri: Uri?, onSharedConsumed: () -> Unit) {
    val navController = rememberNavController()
    val vm: MainViewModel = viewModel()

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
                onSearch = { navController.navigate(Routes.SEARCH) }
            )
        }
        composable(
            route = "${Routes.READER}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            ReaderScreen(
                docId = entry.arguments?.getLong("id") ?: -1L,
                onBack = { navController.popBackStack() }
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
