package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScanPhase
import com.example.model.ScanProgress
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo

@Composable
fun ScanProgressHeader(
    progress: ScanProgress,
    onStartScan: () -> Unit,
    onAutoCleanAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isScanning = progress.phase != ScanPhase.IDLE && progress.phase != ScanPhase.COMPLETED

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scan_progress_header"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PrimaryIndigo.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulse Scanner Badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(if (isScanning) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PrimaryIndigo, PrimaryCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(70.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Scanner",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Phase Title & File progress
                if (isScanning) {
                    val percentage = (progress.progressFraction * 100).toInt()
                    Text(
                        text = when (progress.phase) {
                            ScanPhase.SCANNING_STORAGE -> "Searching device storage..."
                            ScanPhase.ANALYZING_METADATA -> "Analyzing audio tags & metadata..."
                            ScanPhase.COMPUTING_HASHES -> "Computing file content hashes..."
                            ScanPhase.GROUPING_DUPLICATES -> "Grouping duplicate tracks..."
                            else -> "Scanning device for audio files..."
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("scanner_status_text")
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$percentage% Complete",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PrimaryCyan
                        )
                        if (progress.totalCount > 0) {
                            Text(
                                text = " (${progress.processedCount}/${progress.totalCount} files)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (progress.currentFile.isNotEmpty()) {
                        Text(
                            text = "Processing: ${progress.currentFile}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progress.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .testTag("scanner_progress_bar"),
                        color = PrimaryCyan,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    // Completed / Ready Dashboard Summary
                    val reclaimableMb = progress.reclaimableSizeBytes / (1024.0 * 1024.0)
                    val formattedReclaimable = if (reclaimableMb >= 1000) {
                        String.format("%.2f GB", reclaimableMb / 1024.0)
                    } else {
                        String.format("%.1f MB", reclaimableMb)
                    }

                    Text(
                        text = if (progress.foundDuplicatesCount > 0) "$formattedReclaimable Reclaimable Space" else "Audio Storage Clean",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = if (progress.foundDuplicatesCount > 0) EmeraldKeep else MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${progress.foundDuplicatesCount} duplicate tracks ready for review",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scanner Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatBadge("Files Scanned", "${progress.totalCount}")
                        StatBadge("Duplicates", "${progress.foundDuplicatesCount}")
                        StatBadge("Potential Savings", formattedReclaimable)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartScan,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Storage", fontWeight = FontWeight.Bold)
                        }

                        if (progress.foundDuplicatesCount > 0) {
                            Button(
                                onClick = onAutoCleanAll,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldKeep),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("auto_clean_all_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = "Auto Clean",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Clean All", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
