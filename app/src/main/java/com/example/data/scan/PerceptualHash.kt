package com.example.data.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

object PerceptualHash {
    /** 64-bit difference hash. Returns null if the image cannot be decoded. */
    fun dHash(path: String): Long? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= 64 && b.outHeight / (sample * 2) >= 64) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        val bmp = BitmapFactory.decodeFile(path, o) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        if (small !== bmp) bmp.recycle()
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val l = gray(small.getPixel(x, y))
                val r = gray(small.getPixel(x + 1, y))
                if (l > r) hash = hash or (1L shl bit)
                bit++
            }
        }
        small.recycle()
        return hash
    }

    private fun gray(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
