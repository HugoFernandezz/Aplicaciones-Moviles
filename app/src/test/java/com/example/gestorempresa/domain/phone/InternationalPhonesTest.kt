package com.example.gestorempresa.domain.phone

import org.junit.Assert.*
import org.junit.Test

class InternationalPhonesTest {
    @Test fun espanaEsLaSeleccionInicialYSeOfrecenOtrosPaises() {
        assertEquals(PhoneInput("ES", ""), InternationalPhones.restore(""))
        assertEquals("ES", InternationalPhones.countries.first().region)
        assertEquals(34, InternationalPhones.country("ES").callingCode)
        assertEquals("🇪🇸", InternationalPhones.country("ES").flag)
        assertTrue(InternationalPhones.countries.size > 200)
    }

    @Test fun telefonoEspanolSeGuardaConPrefijoYSinSeparadores() {
        assertEquals("+34612345678", InternationalPhones.toE164("612 345 678", "ES"))
        assertEquals("+34612345678", InternationalPhones.toE164("+34 612 345 678", "ES"))
    }

    @Test fun aceptaNumerosNacionalesDeOchoDigitos() {
        assertEquals("+4532123456", InternationalPhones.toE164("32 12 34 56", "DK"))
        assertEquals("+6581234567", InternationalPhones.toE164("8123 4567", "SG"))
        assertEquals("+298211234", InternationalPhones.toE164("211234", "FO"))
    }

    @Test fun respetaLongitudesYCerosSegunElPais() {
        assertEquals("+16502530000", InternationalPhones.toE164("(650) 253-0000", "US"))
        assertEquals("+442079460018", InternationalPhones.toE164("020 7946 0018", "GB"))
        assertEquals("+390236618300", InternationalPhones.toE164("02 3661 8300", "IT"))
    }

    @Test fun editarUnTelefonoInternacionalConservaSuNumeroYPais() {
        listOf("+442079460018" to "GB", "+16502530000" to "US", "+390236618300" to "IT").forEach { (stored, region) ->
            val restored = InternationalPhones.restore(stored)
            assertEquals(region, restored.region)
            assertEquals(stored, InternationalPhones.toE164(restored.number, restored.region))
        }
    }

    @Test fun numerosAntiguosSinPrefijoNoSeRecortanNiSeModificanAlAbrir() {
        val restored = InternationalPhones.restore("612 345 678")
        assertEquals(PhoneInput("ES", "612 345 678"), restored)
        assertEquals("+34612345678", InternationalPhones.toE164(restored.number, restored.region))
        assertEquals("12345678901234567890", InternationalPhones.restore("12345678901234567890").number)
    }

    @Test fun pegarNumeroInternacionalSeleccionaElPaisInclusoConPrefijo00() {
        val pasted = InternationalPhones.edit("0044 20 7946 0018", "ES")
        assertEquals("GB", pasted.region)
        assertEquals("+442079460018", InternationalPhones.toE164(pasted.number, pasted.region))
        assertEquals("US", InternationalPhones.edit("+1 650 253 0000", "ES").region)
    }

    @Test fun rechazaNumerosIncompletosLetrasExtensionesYPrefijosIncompatibles() {
        listOf("", "123", "612ABC678", "612345678 ext 12", "++34612345678", "6+12345678", "9".repeat(20)).forEach {
            assertNull(it, InternationalPhones.toE164(it, "ES"))
        }
        assertNull(InternationalPhones.toE164("+442079460018", "ES"))
        assertNull(InternationalPhones.toE164("612345678", "ZZ"))
        assertEquals(PhoneInput("ES", "abc123"), InternationalPhones.edit("abc123", "ES"))
    }

    @Test fun busquedaDePaisesAdmiteNombreCodigoYPrefijo() {
        assertTrue(InternationalPhones.search("espana").any { it.region == "ES" })
        assertTrue(InternationalPhones.search("+44").any { it.region == "GB" })
        assertTrue(InternationalPhones.search("DK").any { it.region == "DK" })
        assertTrue(InternationalPhones.search("pais inexistente").isEmpty())
    }
}
