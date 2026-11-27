package com.example.gestorempresa.data.media

import kotlin.math.min
import kotlin.math.roundToInt

data class CropBounds(val left: Int, val top: Int, val side: Int)

/** Coordenadas relativas a la imagen: el encuadre no cambia al girar la pantalla. */
data class CropFrame(val centerX: Float = .5f, val centerY: Float = .5f, val zoom: Float = 1f) {
    fun bounded(width: Int, height: Int): CropFrame {
        require(width > 0 && height > 0)
        val z = if (zoom.isFinite()) zoom.coerceIn(1f, 6f) else 1f
        val half = min(width, height) / (2f * z)
        val x = if (centerX.isFinite()) centerX else .5f
        val y = if (centerY.isFinite()) centerY else .5f
        return CropFrame(x.coerceIn(half / width, 1 - half / width), y.coerceIn(half / height, 1 - half / height), z)
    }
    fun bounds(width: Int, height: Int): CropBounds {
        val b = bounded(width, height)
        val side = (min(width, height) / b.zoom).roundToInt().coerceIn(1, min(width, height))
        return CropBounds((b.centerX * width - side / 2f).roundToInt().coerceIn(0, width - side),
            (b.centerY * height - side / 2f).roundToInt().coerceIn(0, height - side), side)
    }
}
