package com.example.gestorempresa.ui.home

import org.junit.Assert.*
import org.junit.Test

class SpanishDniValidationTest {
    @Test fun formatoCorrectoNoBastaSiLaLetraEsIncorrecta() {
        // Ejemplo público del Ministerio; no se guardan documentos de usuarios como fixtures.
        assertFalse(validSpanishDni("12345678N"))
        assertTrue(spanishDniError("12345678N")!!.contains("El formato es correcto"))
        assertTrue(validSpanishDni("12345678Z"))
        assertNull(spanishDniError("12345678Z"))
    }

    @Test fun conservaElControlDeDniYNieYNormalizaEspaciosYMinusculas() {
        listOf("12345678Z", " 12 345 678z ", "X1234567L", "Y1234567X", "Z1234567R").forEach {
            assertTrue(it, validSpanishDni(it))
        }
        assertFalse(validSpanishDni("X1234567N"))
        assertTrue(spanishDniError("X1234567N")!!.contains("letra de control"))
    }

    @Test fun distingueFormatoIncompletoDeCampoObligatorioVacio() {
        listOf("1234567Z", "123456789", "A1234567L", "１２３４５６７８Z").forEach {
            assertTrue(it, spanishDniError(it)!!.startsWith("DNI: 8 números"))
        }
        assertEquals("Introduce el DNI/NIE.", spanishDniError("  "))
    }
}
