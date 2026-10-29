package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.SafaiApp
import com.example.core.Formatters
import com.example.data.repository.HomeStats
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object WidgetData {
    suspend fun stats(ctx: Context): HomeStats =
        (ctx.applicationContext as SafaiApp).container.statsRepository.snapshot()

    fun relative(ctx: Context, ms: Long?): String {
        if (ms == null) return ctx.getString(R.string.widget_never_scanned)
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - ms).toInt()
        return when (days) {
            0 -> ctx.getString(R.string.widget_today)
            1 -> ctx.getString(R.string.widget_yesterday)
            else -> ctx.getString(R.string.widget_days_ago, days)
        }
    }
}

fun openApp(ctx: Context, route: String) = actionStartActivity(
    Intent(ctx, MainActivity::class.java).apply {
        putExtra("route", route)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
)

class StorageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.stats(context)
        provideContent { Content(context, s) }
    }

    @Composable
    private fun Content(ctx: Context, s: HomeStats) {
        val usedFraction = if (s.totalBytes > 0) 1f - (s.freeBytes.toFloat() / s.totalBytes.toFloat()) else 0f
        val wide = LocalSize.current.width >= 250.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg_dark))
                .padding(14.dp)
                .clickable(openApp(ctx, "home")),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ctx.getString(R.string.widget_storage_title),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFB7C4D1)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "${(usedFraction * 100).roundToInt()}% " + ctx.getString(R.string.widget_used),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = if (wide) 26.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = usedFraction,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = ColorProvider(Color(0xFF2DD4BF)),
                backgroundColor = ColorProvider(Color(0xFF263241))
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = ctx.getString(R.string.widget_free, Formatters.formatBytes(s.freeBytes)),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp)
            )
            Text(
                text = if (s.lastScanAt == null) ctx.getString(R.string.widget_tap_to_scan)
                else ctx.getString(R.string.widget_duplicates_line, s.openGroups, Formatters.formatBytes(s.reclaimableBytes)),
                style = TextStyle(color = ColorProvider(Color(0xFFF5C451)), fontSize = 11.sp),
                maxLines = 1
            )
        }
    }
}

class StorageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StorageWidget()
}

class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }

    @Composable
    private fun Content(ctx: Context) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg_surface))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionItem(ctx, "Scan", R.drawable.ic_w_scan, "scan", GlanceModifier.defaultWeight())
            ActionItem(ctx, "Duplicates", R.drawable.ic_w_copy, "duplicates", GlanceModifier.defaultWeight())
            ActionItem(ctx, "Junk", R.drawable.ic_w_clean, "junk", GlanceModifier.defaultWeight())
            ActionItem(ctx, "Storage", R.drawable.ic_w_storage, "storage", GlanceModifier.defaultWeight())
        }
    }

    @Composable
    private fun ActionItem(ctx: Context, label: String, iconRes: Int, route: String, modifier: GlanceModifier) {
        Column(
            modifier = modifier.clickable(openApp(ctx, route)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .background(ImageProvider(R.drawable.widget_chip)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = label,
                    colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Color(0xFF2DD4BF))),
                    modifier = GlanceModifier.size(22.dp)
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp),
                maxLines = 1
            )
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

class CleanedWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 56.dp), DpSize(250.dp, 56.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData.stats(context)
        provideContent { Content(context, s) }
    }

    @Composable
    private fun Content(ctx: Context, s: HomeStats) {
        val wide = LocalSize.current.width >= 250.dp
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg_dark))
                .padding(10.dp)
                .clickable(openApp(ctx, "home")),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = ctx.getString(R.string.widget_freed_so_far),
                    style = TextStyle(color = ColorProvider(Color(0xFFB7C4D1)), fontSize = 10.sp)
                )
                Text(
                    text = Formatters.formatBytes(s.totalFreedBytes),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFF5C451)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = WidgetData.relative(ctx, s.lastScanAt),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp),
                    maxLines = 1
                )
            }

            if (wide) {
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(ImageProvider(R.drawable.widget_chip))
                        .clickable(openApp(ctx, "scan")),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_w_scan),
                        contentDescription = "Scan",
                        colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Color(0xFF2DD4BF))),
                        modifier = GlanceModifier.size(20.dp)
                    )
                }
            }
        }
    }
}

class CleanedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CleanedWidget()
}

object WidgetUpdater {
    suspend fun updateAll(ctx: Context) {
        StorageWidget().updateAll(ctx)
        QuickActionsWidget().updateAll(ctx)
        CleanedWidget().updateAll(ctx)
    }
}
