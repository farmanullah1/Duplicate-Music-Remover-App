package com.example.ui.nav

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.duplicates.CompareScreen
import com.example.ui.duplicates.DuplicatesScreen
import com.example.ui.home.HomeScreen
import com.example.ui.junk.JunkScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.recyclebin.RecycleBinScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.storage.StorageScreen
import com.example.ui.theme.Motion
import kotlinx.coroutines.launch

@Composable
fun SafaiNavHost(
    navController: NavHostController,
    startDestination: String,
    snackbarHostState: SnackbarHostState,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun showSnackbarWithAction(message: String, actionLabel: String, onAction: () -> Unit) {
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300, easing = Motion.Emphasized)) { it / 12 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it / 12 } },
        modifier = modifier.padding(innerPadding)
    ) {
        composable(Route.Onboarding.path) {
            OnboardingScreen(
                onFinished = { startScan ->
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Onboarding.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Home.path) {
            HomeScreen(
                onNavigateToDuplicates = { cat ->
                    if (cat != null) navController.navigate("duplicates?category=$cat")
                    else navController.navigate(Route.Duplicates.path)
                },
                onNavigateToJunk = { navController.navigate(Route.Junk.path) },
                onNavigateToStorage = { navController.navigate(Route.Storage.path) },
                onNavigateToRecycleBin = { navController.navigate(Route.RecycleBin.path) },
                onShowSnackbar = { showSnackbar(it) }
            )
        }

        composable(
            route = "duplicates?category={category}",
            arguments = listOf(navArgument("category") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val cat = backStackEntry.arguments?.getString("category")
            DuplicatesScreen(
                initialCategory = cat,
                onNavigateToCompare = { groupId ->
                    navController.navigate(Route.Compare.go(groupId))
                },
                onNavigateToScan = { navController.navigate(Route.Home.path) },
                onShowSnackbarWithAction = { msg, act, fn -> showSnackbarWithAction(msg, act, fn) },
                onShowSnackbar = { showSnackbar(it) }
            )
        }

        composable(Route.Duplicates.path) {
            DuplicatesScreen(
                initialCategory = null,
                onNavigateToCompare = { groupId ->
                    navController.navigate(Route.Compare.go(groupId))
                },
                onNavigateToScan = { navController.navigate(Route.Home.path) },
                onShowSnackbarWithAction = { msg, act, fn -> showSnackbarWithAction(msg, act, fn) },
                onShowSnackbar = { showSnackbar(it) }
            )
        }

        composable(
            route = Route.Compare().path,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toLongOrNull() ?: -1L
            CompareScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() },
                onShowSnackbar = { showSnackbar(it) }
            )
        }

        composable(Route.Junk.path) {
            JunkScreen(
                onShowSnackbar = { showSnackbar(it) }
            )
        }

        composable(Route.Storage.path) {
            StorageScreen(
                onShowSnackbar = { showSnackbar(it) }
            )
        }

        composable(Route.RecycleBin.path) {
            RecycleBinScreen(
                onNavigateBack = { navController.popBackStack() },
                onShowSnackbar = { showSnackbar(it) }
            )
        }

        composable(Route.Settings.path) {
            SettingsScreen(
                onShowSnackbar = { showSnackbar(it) }
            )
        }
    }
}
