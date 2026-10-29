package com.example.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.Sizes
import com.example.ui.theme.Spacing

val LocalWindowWidthSizeClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

fun matchesRoute(currentRoute: String?, targetRoute: String): Boolean {
    if (currentRoute == null) return false
    val c = currentRoute.substringBefore('?').substringBefore('/')
    val t = targetRoute.substringBefore('?').substringBefore('/')
    return c == t
}

data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination("home", R.string.tab_home, Icons.Rounded.Home),
    TopLevelDestination("duplicates", R.string.tab_duplicates, Icons.Rounded.ContentCopy),
    TopLevelDestination("junk", R.string.tab_junk, Icons.Rounded.CleaningServices),
    TopLevelDestination("storage", R.string.tab_storage, Icons.Rounded.PieChart),
    TopLevelDestination("settings", R.string.tab_settings, Icons.Rounded.Settings)
)

@Composable
fun AdaptiveScaffold(
    currentRoute: String?,
    onNavigateToDestination: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val widthClass = LocalWindowWidthSizeClass.current
    val haptic = LocalHapticFeedback.current

    val isTopLevel = currentRoute != null && TOP_LEVEL_DESTINATIONS.any { matchesRoute(currentRoute, it.route) }

    when {
        widthClass == WindowWidthSizeClass.Expanded -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(Sizes.drawerWidth),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Spacer(Modifier.height(Spacing.xl))
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Sizes.iconLg)
                            )
                            Text(
                                text = "Safai",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(Spacing.xl))
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            NavigationDrawerItem(
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(stringResource(dest.labelRes)) },
                                selected = matchesRoute(currentRoute, dest.route),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateToDestination(dest.route)
                                },
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                            )
                        }
                    }
                },
                content = {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier = modifier
                    ) { innerPadding ->
                        content(innerPadding)
                    }
                }
            )
        }
        widthClass == WindowWidthSizeClass.Medium -> {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.width(Sizes.railWidth),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Spacer(Modifier.height(Spacing.lg))
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Sizes.iconLg)
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    TOP_LEVEL_DESTINATIONS.forEach { dest ->
                        NavigationRailItem(
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes), fontSize = 11.sp) },
                            selected = matchesRoute(currentRoute, dest.route),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToDestination(dest.route)
                            }
                        )
                    }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = modifier.weight(1f)
                ) { innerPadding ->
                    content(innerPadding)
                }
            }
        }
        else -> {
            // Compact: Floating bottom bar overlaid on content
            Box(modifier = modifier.fillMaxSize()) {
                Scaffold(
                    snackbarHost = {
                        Box(modifier = Modifier.padding(bottom = if (isTopLevel) Sizes.bottomBar + 28.dp else 16.dp)) {
                            SnackbarHost(snackbarHostState)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val topPadding = innerPadding.calculateTopPadding()
                    val bottomPadding = if (isTopLevel) Sizes.bottomBar + 16.dp else innerPadding.calculateBottomPadding()
                    content(PaddingValues(top = topPadding, bottom = bottomPadding))
                }

                if (isTopLevel) {
                    FloatingBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = onNavigateToDestination,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.bottomBar),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TOP_LEVEL_DESTINATIONS.forEach { dest ->
                    val selected = matchesRoute(currentRoute, dest.route)
                    val tint by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "icon_tint"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .then(
                                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                else Modifier
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigate(dest.route)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = stringResource(dest.labelRes),
                                tint = tint,
                                modifier = Modifier.size(Sizes.icon)
                            )
                            Text(
                                text = stringResource(dest.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
