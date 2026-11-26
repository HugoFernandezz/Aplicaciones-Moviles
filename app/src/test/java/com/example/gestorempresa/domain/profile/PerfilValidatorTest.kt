package com.example.gestorempresa.domain.profile

import org.junit.Assert.*
import org.junit.Test

class PerfilValidatorTest {
    @Test fun telefonoInternacionalNoNecesitaNueveDigitosNacionales() {
        val input = DatosPerfil("Ana", "Pérez", "32123456", "", "", "Empresa", "Limpieza", telefonoRegion = "DK")
        assertTrue(PerfilValidator.validar(input).isEmpty())
        assertEquals("+4532123456", PerfilValidator.normalizar(input).telefono)
    }
    @Test fun editarOtrosDatosConservaElPrefijoDelPerfil() {
        val perfil = PerfilAdministrador(1, "Ana", "Pérez", "a@empresa.es", "ADMIN", "+442079460018", "", "", null,
            com.example.gestorempresa.domain.auth.Empresa(1, "Empresa", "Limpieza"))
        val input = perfil.toDatosPerfil().copy(nombre = "Andrea", direccion = "Calle Mayor 1")
        assertEquals("GB", input.telefonoRegion)
        assertEquals("+442079460018", PerfilValidator.normalizar(input).telefono)
    }
    @Test fun laSeleccionDePaisSeConservaInclusoSinTelefono() {
        val perfil = PerfilAdministrador(1, "Ana", "Pérez", "a@empresa.es", "ADMIN", "", "", "", null,
            com.example.gestorempresa.domain.auth.Empresa(1, "Empresa", "Limpieza"), telefonoRegion = "DK")
        assertEquals("DK", perfil.toDatosPerfil().telefonoRegion)
    }
    @Test fun direccionTieneUnLimiteYSeNormaliza() {
        assertTrue(CampoPerfil.DIRECCION in PerfilValidator.validar(DatosPerfil(direccion = "x".repeat(241))))
        assertEquals("Calle Mayor 1", PerfilValidator.normalizar(DatosPerfil(direccion = " Calle Mayor 1 ")).direccion)
    }
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
