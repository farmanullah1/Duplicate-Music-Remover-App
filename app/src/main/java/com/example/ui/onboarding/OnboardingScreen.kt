package com.example.ui.onboarding

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.StorageAccess
import com.example.di.LocalAppContainer
import com.example.di.viewModelFactory
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: (Boolean) -> Unit,
    viewModel: OnboardingViewModel = run {
        val c = LocalAppContainer.current
        viewModel(factory = viewModelFactory { OnboardingViewModel(c.settingsRepository) })
    }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var storageMode by remember { mutableStateOf(StorageAccess.mode(context)) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        storageMode = StorageAccess.mode(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage(
                        onNext = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    1 -> OnboardingPermissionsPage(
                        storageMode = storageMode,
                        onGrantAllFiles = {
                            try {
                                context.startActivity(StorageAccess.allFilesIntent(context))
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(StorageAccess.appDetailsIntent(context))
                                } catch (ignored: Exception) {}
                            }
                        },
                        onGrantMedia = {
                            mediaPermissionLauncher.launch(StorageAccess.mediaPermissions())
                        },
                        onNext = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                    )
                    2 -> OnboardingReadyPage(
                        cleanupReminder = settings.cleanupReminder,
                        onToggleReminder = { viewModel.setCleanupReminder(it) },
                        onFinish = { startScan ->
                            viewModel.finishOnboarding(startScan)
                            onFinished(startScan)
                        }
                    )
                }
            }

            // Pager Indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xl),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (isSelected) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(Spacing.xxl))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(
                icon = Icons.Rounded.AutoAwesome,
                tint = MaterialTheme.colorScheme.primary,
                size = 72.dp
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            BenefitRow(
                icon = Icons.Rounded.ContentCopy,
                title = "Smart Duplicate Removal",
                desc = "Find identical music, photos, videos, and documents safely with SHA-256."
            )
            BenefitRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Safe Recycle Bin",
                desc = "30-day protection window. Restore files anytime if you change your mind."
            )
            BenefitRow(
                icon = Icons.Rounded.PieChart,
                title = "Storage Explorer & Junk",
                desc = "Inspect folder-by-folder usage and clean temporary junk files."
            )
        }

        PrimaryButton(
            text = stringResource(R.string.onboarding_get_started),
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, tint = MaterialTheme.colorScheme.primary, size = 44.dp)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnboardingPermissionsPage(
    storageMode: StorageAccess.Mode,
    onGrantAllFiles: () -> Unit,
    onGrantMedia: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Storage Access",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Safai works 100% offline. To find duplicates and clean junk, select how much access you want to provide.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.lg))

            // Card A: Full access
            SafaiCard(
                tonal = storageMode == StorageAccess.Mode.FULL,
                onClick = onGrantAllFiles
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Full access (Recommended)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (storageMode == StorageAccess.Mode.FULL) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Success)
                            }
                        }
                        Text(
                            text = "Scan photos, videos, music, documents, archives, APKs, and temporary junk files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // Card B: Media only
            SafaiCard(
                tonal = storageMode == StorageAccess.Mode.MEDIA_ONLY,
                onClick = onGrantMedia
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Media only",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (storageMode == StorageAccess.Mode.MEDIA_ONLY) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Success)
                            }
                        }
                        Text(
                            text = "Access photos, videos, and music only. Documents and junk cleaner will be disabled.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                text = "Continue",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = onNext,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnboardingReadyPage(
    cleanupReminder: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    onFinish: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(Spacing.xl))
            IconBadge(
                icon = Icons.Rounded.Verified,
                tint = Success,
                size = 64.dp
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "You're all set!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Safai is ready to help you keep your phone tidy, organized, and free of wasted storage.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xl))

            SafaiCard(tonal = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Monthly cleanup reminder",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Receive a gentle nudge once a month to clear accumulated duplicates and junk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cleanupReminder,
                        onCheckedChange = onToggleReminder
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PrimaryButton(
                text = "Scan Now",
                onClick = { onFinish(true) },
                icon = Icons.Rounded.Radar,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onFinish(false) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Text("Go to Home", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
