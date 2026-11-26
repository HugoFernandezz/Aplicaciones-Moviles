package com.example.gestorempresa.domain.profile

import org.junit.Assert.*
import org.junit.Test

class PerfilValidatorTest {
    private val validos = DatosPerfil(
        nombre = "Juan", apellidos = "Pérez López", telefono = "+34 600 123 456",
        dni = "12345678Z", cargo = "Gerente", empresaNombre = "Construcciones Murcia", sector = "Construcción"
    )

    @Test fun datosCorrectosNoTienenErrores() {
        assertTrue(PerfilValidator.validar(validos).isEmpty())
    }

    @Test fun telefonoDniYCargoSonOpcionales() {
        val datos = validos.copy(telefono = "", dni = "", cargo = "")
        assertTrue(PerfilValidator.validar(datos).isEmpty())
    }

    @Test fun detectaCamposObligatoriosVacios() {
        val errores = PerfilValidator.validar(DatosPerfil())
        assertEquals(
            setOf(CampoPerfil.NOMBRE, CampoPerfil.APELLIDOS, CampoPerfil.EMPRESA, CampoPerfil.SECTOR),
            errores.keys
        )
    }

    @Test fun aceptaNieYRechazaDniMalFormado() {
        assertTrue(PerfilValidator.validar(validos.copy(dni = "X1234567L")).isEmpty())
        assertTrue(CampoPerfil.DNI in PerfilValidator.validar(validos.copy(dni = "1234")))
    }

    @Test fun rechazaTelefonoConLetras() {
        assertTrue(CampoPerfil.TELEFONO in PerfilValidator.validar(validos.copy(telefono = "600abc123")))
    }

    @Test fun normalizarQuitaEspaciosYPoneDniEnMayusculas() {
        val limpio = PerfilValidator.normalizar(validos.copy(nombre = "  Juan ", dni = " 12345678z "))
        assertEquals("Juan", limpio.nombre)
        assertEquals("12345678Z", limpio.dni)
    }
}
