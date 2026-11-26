package com.example.gestorempresa.domain.profile

import com.example.gestorempresa.data.media.CropFrame
import org.junit.Assert.*
import org.junit.Test

class CropFrameTest {
    @Test fun encuadreInicialCentraElCuadradoSinDeformar() {
        val landscape = CropFrame().bounds(900, 300)
        assertEquals(300, landscape.left); assertEquals(0, landscape.top); assertEquals(300, landscape.side)
        val portrait = CropFrame().bounds(300, 900)
        assertEquals(0, portrait.left); assertEquals(300, portrait.top); assertEquals(300, portrait.side)
    }
    @Test fun ampliarReduceLaZonaRecortadaYConservaElCentro() {
        val cut = CropFrame(zoom = 2f).bounds(900, 300)
        assertEquals(150, cut.side); assertEquals(375, cut.left); assertEquals(75, cut.top)
    }
    @Test fun desplazarNuncaDejaElRecorteFueraDeLaImagen() {
        listOf(-100f, 0f, .5f, 1f, 100f, Float.NaN).forEach { x ->
            listOf(-100f, .5f, 100f, Float.NaN).forEach { y ->
                listOf(-1f, 1f, 3f, 100f, Float.NaN).forEach { zoom ->
                    val cut = CropFrame(x, y, zoom).bounds(900, 300)
                    assertTrue(cut.left >= 0 && cut.top >= 0 && cut.side > 0)
                    assertTrue(cut.left + cut.side <= 900 && cut.top + cut.side <= 300)
                }
            }
        }
    }
    @Test fun zoomMinimoYMaximoEstanAcotados() {
        assertEquals(300, CropFrame(zoom = -3f).bounds(900, 300).side)
        assertEquals(50, CropFrame(zoom = 30f).bounds(900, 300).side)
    }
}
