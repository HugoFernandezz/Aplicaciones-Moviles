package com.example.gestorempresa.auth

import com.example.gestorempresa.domain.auth.*
import org.junit.Assert.*
import org.junit.Test

class AuthValidatorTest {
    private val valid = RegistroUsuario("Irene", "García", "admin@empresa.es", "mi frase de acceso", "mi frase de acceso", "Servicios Murcia", "Limpieza")
    @Test fun registroValido() { assertTrue(AuthValidator.validarRegistro(valid).isEmpty()) }
    @Test fun normalizaEmail() { assertEquals("admin@empresa.es", AuthValidator.normalizarEmail("  ADMIN@Empresa.ES ")) }
    @Test fun rechazaVacios() { assertEquals(7, AuthValidator.validarRegistro(RegistroUsuario("", "", "", "", "x", "", "")).size) }
    @Test fun rechazaCorreoMalformado() { assertTrue(CampoAuth.EMAIL in AuthValidator.validarLogin("a@", "password")) }
    @Test fun rechazaClaveCorta() { assertTrue(CampoAuth.PASSWORD in AuthValidator.validarRegistro(valid.copy(password = "abc", confirmarPassword = "abc"))) }
    @Test fun rechazaConfirmacionDistinta() { assertTrue(CampoAuth.CONFIRMACION in AuthValidator.validarRegistro(valid.copy(confirmarPassword = "otra"))) }
    @Test fun noRecortaPassword() { assertTrue(CampoAuth.CONFIRMACION in AuthValidator.validarRegistro(valid.copy(confirmarPassword = valid.password + " "))) }
    @Test fun sectorDebeExistir() { assertTrue(CampoAuth.SECTOR in AuthValidator.validarRegistro(valid.copy(sector = "inventado"))) }
    @Test fun limitaLongitud() { assertTrue(CampoAuth.PASSWORD in AuthValidator.validarLogin(valid.email, "x".repeat(129))) }
    @Test fun loginRemotoAdmiteClavesRestablecidasSinAmpliarElRegistro() {
        assertTrue(AuthValidator.validarLogin(valid.email, "x".repeat(4096), maxLength = 4096).isEmpty())
        assertTrue(CampoAuth.PASSWORD in AuthValidator.validarLogin(valid.email, "x".repeat(4097), maxLength = 4096))
        assertTrue(CampoAuth.PASSWORD in AuthValidator.validarRegistro(valid.copy(password = "x".repeat(129), confirmarPassword = "x".repeat(129))))
    }
}
