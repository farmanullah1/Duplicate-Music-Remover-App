package com.example.data.storage

import android.graphics.BitmapFactory
import android.graphics.Color

object ImageQuality {
    data class Result(val sharpness: Float, val brightness: Float)

    /** Variance of the Laplacian on a ≤256 px grayscale copy. Low variance = few edges = blurry. */
    fun analyze(path: String): Result? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) {
            bmp.recycle()
            return null
        }
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()

        val g = FloatArray(w * h) {
            val c = px[it]
            0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }

        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        var bright = 0.0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val v = 4f * g[i] - g[i - w] - g[i + w] - g[i - 1] - g[i + 1]
                sum += v
                sumSq += v * v
                n++
                bright += g[i]
            }
        }

        if (n == 0) return null
        val mean = sum / n
        val variance = (sumSq / n) - (mean * mean)

        return Result(variance.toFloat().coerceAtLeast(0f), (bright / n).toFloat())
    }
}
