package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.settings.AppSettings
import com.example.ui.nav.*
import com.example.ui.theme.SafaiTheme

class MainActivity : ComponentActivity() {

    private var initialRouteExtra: String? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SafaiApp).container
        val settingsRepository = container.settingsRepository

        initialRouteExtra = intent?.getStringExtra("route")

        setContent {
            val settings by settingsRepository.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val windowSizeClass = calculateWindowSizeClass(this)
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }

            val startDest = if (!settings.onboardingDone) Route.Onboarding.path else Route.Home.path

            LaunchedEffect(initialRouteExtra) {
                val route = initialRouteExtra
                if (!route.isNullOrBlank() && settings.onboardingDone) {
                    when (route) {
                        "home", "scan" -> navController.navigate(Route.Home.path)
                        "duplicates" -> navController.navigate(Route.Duplicates.path)
                        "junk" -> navController.navigate(Route.Junk.path)
                        "storage" -> navController.navigate(Route.Storage.path)
                        "recycle_bin" -> navController.navigate(Route.RecycleBin.path)
                        "studio", "ai_music_studio" -> navController.navigate(Route.AiMusicStudio.path)
                    }
                    initialRouteExtra = null
                }
            }

            CompositionLocalProvider(
                com.example.di.LocalAppContainer provides container,
                LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass
            ) {
                SafaiTheme(settings = settings) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    AdaptiveScaffold(
                        currentRoute = currentRoute,
                        onNavigateToDestination = { destRoute ->
                            navController.navigate(destRoute) {
                                popUpTo(Route.Home.path) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        snackbarHostState = snackbarHostState
                    ) { innerPadding ->
                        SafaiNavHost(
                            navController = navController,
                            startDestination = startDest,
                            snackbarHostState = snackbarHostState,
                            innerPadding = innerPadding
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialRouteExtra = intent.getStringExtra("route")
    }
}
