package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.ui.theme.CrimsonDelete
import com.example.ui.theme.EmeraldKeep
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.PrimaryIndigo

data class FormatBreakdown(
    val format: String,
    val count: Int,
    val sizeBytes: Long,
    val percentage: Float,
    val color: Color
)

private val FORMAT_COLORS = listOf(
    Color(0xFF38BDF8), // Cyan for MP3
    Color(0xFF818CF8), // Indigo for FLAC
    Color(0xFF34D399), // Emerald for WAV
    Color(0xFFFBBF24), // Amber for M4A
    Color(0xFFA78BFA), // Purple for AAC
    Color(0xFFF472B6), // Pink for OGG
    Color(0xFF94A3B8)  // Slate for Others
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBreakdownChart(
    allFiles: List<AudioFile>,
    duplicateGroups: List<DuplicateGroup>,
    modifier: Modifier = Modifier
) {
    if (allFiles.isEmpty()) return

    val totalSizeBytes = remember(allFiles) { allFiles.sumOf { it.sizeBytes } }
    val duplicateSavingsBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { it.potentialSavingsBytes }
    }

    val duplicateWasteRatio = if (totalSizeBytes > 0) {
        (duplicateSavingsBytes.toFloat() / totalSizeBytes).coerceIn(0f, 1f)
    } else 0f

    val animatedWasteRatio by animateFloatAsState(
        targetValue = duplicateWasteRatio,
        animationSpec = tween(durationMillis = 800),
        label = "waste_ratio"
    )

    // Calculate distribution by audio format
    val formatBreakdowns = remember(allFiles, totalSizeBytes) {
        val grouped = allFiles.groupBy { it.format.uppercase() }
        grouped.entries.mapIndexed { index, entry ->
            val formatSize = entry.value.sumOf { it.sizeBytes }
            val pct = if (totalSizeBytes > 0) formatSize.toFloat() / totalSizeBytes else 0f
            val color = FORMAT_COLORS[index % FORMAT_COLORS.size]
            FormatBreakdown(
                format = entry.key,
                count = entry.value.size,
                sizeBytes = formatSize,
                percentage = pct,
                color = color
            )
        }.sortedByDescending { it.sizeBytes }
    }

    // Calculate quality tier distribution
    val qualityTiers = remember(allFiles) {
        val hiRes = allFiles.count { it.bitrateKbps >= 320 || it.format.equals("FLAC", true) || it.format.equals("WAV", true) }
        val high = allFiles.count { it.bitrateKbps in 220..319 }
        val standard = allFiles.count { it.bitrateKbps in 128..219 }
        val low = allFiles.count { it.bitrateKbps in 1..127 }
        listOf(
            Triple("Hi-Res / Lossless", hiRes, EmeraldKeep),
            Triple("High Quality (320k)", high, PrimaryCyan),
            Triple("Standard (128-192k)", standard, PrimaryIndigo),
            Triple("Compressed (<128k)", low, Color(0xFF94A3B8))
        ).filter { it.second > 0 }
    }

    var selectedFormat by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_breakdown_analytics_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Analytics",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Storage & Format Analytics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${allFiles.size} tracks (${formatBytes(totalSizeBytes)}) analyzed",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (duplicateWasteRatio > 0.05f) CrimsonDelete.copy(alpha = 0.12f) else EmeraldKeep.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${(duplicateWasteRatio * 100).toInt()}% Duplicates",
                        color = if (duplicateWasteRatio > 0.05f) CrimsonDelete else EmeraldKeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-segment stacked horizontal bar
            Text(
                text = "Format Storage Allocation",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    formatBreakdowns.forEach { item ->
                        val weight = item.percentage.coerceAtLeast(0.01f)
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(item.color)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Format Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                formatBreakdowns.forEach { item ->
                    val isSelected = selectedFormat == item.format
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedFormat = if (isSelected) null else item.format
                            },
                        color = if (isSelected) item.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) item.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(item.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${item.format} • ${formatBytes(item.sizeBytes)} (${(item.percentage * 100).toInt()}%)",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quality Tiers Summary
            Text(
                text = "Audio Fidelity Distribution",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualityTiers.forEach { tier ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, tier.third.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "${tier.second}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = tier.third,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = tier.first,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.1f KB", kb)
    }
}
