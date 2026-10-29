package com.example.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
    fun formatBytes(b: Long): String {
        if (b < 1000) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = -1
        do {
            v /= 1000.0
            i++
        } while (v >= 1000.0 && i < units.lastIndex)
        val pattern = if (v >= 100) "%.0f %s" else if (v >= 10) "%.1f %s" else "%.2f %s"
        return String.format(Locale.US, pattern, v, units[i])
    }

    fun formatDuration(durationMs: Long): String {
        val totalSecs = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatDate(timeMs: Long): String {
        if (timeMs <= 0) return "Unknown date"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }
}
