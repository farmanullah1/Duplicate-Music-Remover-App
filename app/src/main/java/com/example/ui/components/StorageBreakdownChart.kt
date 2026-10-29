package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.Formatters
import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.ui.theme.CatAudio
import com.example.ui.theme.Danger
import com.example.ui.theme.Gold400
import com.example.ui.theme.Spacing
import com.example.ui.theme.Success
import com.example.ui.theme.Teal400
import com.example.ui.theme.Violet400
import com.example.ui.theme.Warning

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
    allFiles: List<MediaFile>,
    duplicateGroups: List<DuplicateGroupUi>,
    onSelectFormatFilter: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val audioFiles = remember(allFiles) {
        allFiles.filter { it.category == MediaCategory.AUDIO }
    }
    if (audioFiles.isEmpty()) return

    val totalSizeBytes = remember(audioFiles) { audioFiles.sumOf { it.sizeBytes } }
    val audioDuplicateGroups = remember(duplicateGroups) {
        duplicateGroups.filter { it.category == MediaCategory.AUDIO }
    }
    val duplicateSavingsBytes = remember(audioDuplicateGroups) {
        audioDuplicateGroups.sumOf { it.reclaimableBytes }
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
    val formatBreakdowns = remember(audioFiles, totalSizeBytes) {
        val grouped = audioFiles.groupBy { it.extension.uppercase() }
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
    val qualityTiers = remember(audioFiles) {
        val hiRes = audioFiles.count { (it.bitrateKbps ?: 0) >= 320 || it.extension.equals("FLAC", true) || it.extension.equals("WAV", true) }
        val high = audioFiles.count { (it.bitrateKbps ?: 0) in 220..319 }
        val standard = audioFiles.count { (it.bitrateKbps ?: 0) in 128..219 }
        val low = audioFiles.count { (it.bitrateKbps ?: 0) < 128 && !it.extension.equals("FLAC", true) && !it.extension.equals("WAV", true) }

        listOf(
            Triple("Hi-Res / Lossless", hiRes, Teal400),
            Triple("High (320k)", high, Success),
            Triple("Standard", standard, Gold400),
            Triple("Compressed", low, Warning)
        )
    }

    var selectedFormat by remember { mutableStateOf<String?>(null) }

    SafaiCard(
        modifier = modifier.fillMaxWidth(),
        tonal = true
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CatAudio.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = CatAudio,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Audio Storage & Fidelity Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${audioFiles.size} music tracks · ${Formatters.formatBytes(totalSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Savings & Waste Overview Metric Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedWasteRatio },
                                modifier = Modifier.size(42.dp),
                                color = Danger,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "${(duplicateWasteRatio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (duplicateWasteRatio > 0.15f) Danger else Success
                            )
                        }
                        Column {
                            Text(
                                text = "Duplicate Waste",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatBytes(duplicateSavingsBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Danger
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Savings,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Recoverable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${audioDuplicateGroups.size} groups",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Success
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Multi-color segmented distribution bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Format Chips with tap-to-filter
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                formatBreakdowns.forEach { item ->
                    val isSelected = selectedFormat == item.format
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedFormat = if (isSelected) null else item.format
                                onSelectFormatFilter?.invoke(selectedFormat)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) item.color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (isSelected) item.color else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(item.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${item.format} · ${Formatters.formatBytes(item.sizeBytes)} (${(item.percentage * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Fidelity Tiers Summary
            Text(
                text = "Audio Fidelity Distribution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                qualityTiers.forEach { tier ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, tier.third.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${tier.second}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = tier.third,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = tier.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

