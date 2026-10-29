package com.example.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.Formatters
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SafaiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tonal: Boolean = false,
    brush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (brush != null) Modifier.background(brush)
            else Modifier.background(
                if (tonal) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer
            )
        )
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .then(if (onClick != null) Modifier.pressScaleClickable(onClick) else Modifier)

    Column(modifier.then(base).padding(Spacing.md), content = content)
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        action?.invoke()
    }
}

@Composable
fun StatPill(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Sizes.iconSm)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .then(if (enabled) Modifier.pressScaleClickable(onClick) else Modifier),
        shape = shape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (enabled) BrandGradient else Brush.linearGradient(listOf(Color.DarkGray, Color.Gray)))
                .padding(horizontal = Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(Sizes.icon)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .then(if (enabled) Modifier.pressScaleClickable(onClick) else Modifier),
        shape = shape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (enabled) DangerGradient else Brush.linearGradient(listOf(Color.DarkGray, Color.Gray)))
                .padding(horizontal = Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(Sizes.icon)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
fun CategoryBadge(category: MediaCategory, modifier: Modifier = Modifier) {
    val (color, name) = when (category) {
        MediaCategory.AUDIO -> CatAudio to "Audio"
        MediaCategory.IMAGE -> CatImage to "Photo"
        MediaCategory.VIDEO -> CatVideo to "Video"
        MediaCategory.DOCUMENT -> CatDocument to "Doc"
        MediaCategory.ARCHIVE -> CatArchive to "Archive"
        MediaCategory.APK -> CatApk to "APK"
        MediaCategory.OTHER -> CatOther to "Other"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ConfidenceBadge(score: Float, modifier: Modifier = Modifier) {
    val (label, color) = when {
        score >= 0.95f -> "Certain" to Success
        score >= 0.70f -> "Likely" to Warning
        else -> "Review" to Danger
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBadge(icon = icon, tint = MaterialTheme.colorScheme.primary, size = Sizes.iconHero)
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    text: String,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SafaiCard(
        modifier = modifier,
        tonal = true
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            IconBadge(
                icon = Icons.Rounded.Security,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit
) {
    val animProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = Motion.Emphasized),
        label = "ring_progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            // Track
            drawArc(
                color = Color(0xFF263241),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            // Segments
            var currentStart = -90f
            for ((segFraction, segColor) in segments) {
                val sweep = 360f * segFraction * animProgress
                if (sweep > 0f) {
                    drawArc(
                        color = segColor,
                        startAngle = currentStart,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke
                    )
                    currentStart += sweep
                }
            }
        }
        centerContent()
    }
}

@Composable
fun AnimatedCounterText(
    value: Long,
    modifier: Modifier = Modifier,
    format: (Long) -> String = { Formatters.formatBytes(it) },
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val animValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(700, easing = Motion.Emphasized),
        label = "counter"
    )
    Text(
        text = format(animValue.toLong()),
        style = style,
        color = color,
        modifier = modifier
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmer()
    )
}

@Composable
fun SelectionBottomBar(
    count: Int,
    size: Long,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Clean"
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "$count files · ${Formatters.formatBytes(size)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Clear selection",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onClear() }
                )
            }
            DangerButton(
                text = "$label ${Formatters.formatBytes(size)}",
                onClick = onDelete,
                modifier = Modifier.height(44.dp)
            )
        }
    }
}

@Composable
fun ThumbnailImage(
    file: MediaFile,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.thumb
) {
    val shape = RoundedCornerShape(12.dp)
    val base = modifier
        .size(size)
        .clip(shape)

    when (file.category) {
        MediaCategory.IMAGE -> {
            AsyncImage(
                model = file.uri ?: file.path,
                contentDescription = file.name,
                modifier = base,
                contentScale = ContentScale.Crop
            )
        }
        MediaCategory.VIDEO -> {
            Box(
                modifier = base,
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = file.uri ?: file.path,
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(size * 0.45f)
                    )
                }
            }
        }
        MediaCategory.AUDIO -> {
            var albumArt by remember(file.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(file.path) {
                withContext(Dispatchers.IO) {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(file.path)
                        val pic = r.embeddedPicture
                        if (pic != null) {
                            albumArt = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                        }
                    } catch (e: Exception) {
                    } finally {
                        runCatching { r.release() }
                    }
                }
            }

            if (albumArt != null) {
                androidx.compose.foundation.Image(
                    bitmap = albumArt!!.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = base,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = base.background(CatAudio.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = CatAudio,
                        modifier = Modifier.size(size * 0.5f)
                    )
                }
            }
        }
        MediaCategory.DOCUMENT -> {
            val ext = file.extension.uppercase().take(4)
            Box(
                modifier = base.background(CatDocument.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ext,
                    style = MaterialTheme.typography.labelSmall,
                    color = CatDocument,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        MediaCategory.ARCHIVE -> {
            Box(
                modifier = base.background(CatArchive.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderZip,
                    contentDescription = null,
                    tint = CatArchive,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
        MediaCategory.APK -> {
            Box(
                modifier = base.background(CatApk.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    tint = CatApk,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
        else -> {
            Box(
                modifier = base.background(CatOther.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = CatOther,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}
