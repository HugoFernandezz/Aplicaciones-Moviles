package com.example.gestorempresa.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodificador compartido: limita memoria y respeta todas las orientaciones EXIF. */
fun decodePhotoBitmap(path: String, maxSide: Int = 2048): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        bitmap?.let { original ->
            val orientation = runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> { setRotate(180f); postScale(-1f, 1f) }
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            val rotated = if (matrix.isIdentity) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            val scale = maxSide.toFloat() / max(rotated.width, rotated.height)
            if (scale < 1) Bitmap.createScaledBitmap(rotated, (rotated.width * scale).roundToInt().coerceAtLeast(1),
                (rotated.height * scale).roundToInt().coerceAtLeast(1), true) else rotated
        }
    }
} catch (_: Exception) { null }
