package com.example.gestorempresa.auth

import androidx.lifecycle.SavedStateHandle
import com.example.gestorempresa.domain.auth.*
import com.example.gestorempresa.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @Test fun corregirUnCampoConservaLosOtrosErroresYLosDatosIntroducidos() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeRepository(), SavedStateHandle())
        vm.registrar()
        assertEquals(1, vm.ui.value.intentoValidacion)
        assertTrue(CampoAuth.CONFIRMACION in vm.ui.value.errores)
        vm.editarRegistro(vm.ui.value.registro.copy(nombre = "Ana"))
        assertFalse(CampoAuth.NOMBRE in vm.ui.value.errores)
        assertTrue(CampoAuth.EMAIL in vm.ui.value.errores)
        assertTrue(CampoAuth.SECTOR in vm.ui.value.errores)
        assertEquals("Ana", vm.ui.value.registro.nombre)
        vm.registrar()
        assertEquals(2, vm.ui.value.intentoValidacion)
    }

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeRepository : AuthRepository {
        override val sesion = MutableStateFlow<SesionUsuario?>(null)
        var logins = 0
        var solicitudes = 0
        var ultimoCorreo = ""
        var resultadoRecuperacion: ResultadoAuth = ResultadoAuth.Correcto
        var resultadoRegistro: ResultadoAuth = ResultadoAuth.Correcto
        override suspend fun registrar(datos: RegistroUsuario) = resultadoRegistro
        override suspend fun iniciarSesion(email: String, password: String): ResultadoAuth { logins++; return ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS) }
        override suspend fun cerrarSesion() = ResultadoAuth.Correcto
        override suspend fun recuperarPassword(email: String): ResultadoAuth {
            solicitudes++; ultimoCorreo = email; return resultadoRecuperacion
        }
    }

    @Test fun validacionImpideConsultarRepositorio() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = AuthViewModel(repo, SavedStateHandle())
        vm.iniciarSesion(); runCurrent()
        assertEquals(0, repo.logins)
        assertTrue(vm.ui.value.errores.isNotEmpty())
    }
    @Test fun correoDuplicadoDelServidorMarcaElCampoYConservaElRegistro() = runTest(dispatcher) {
        val repo = FakeRepository().apply { resultadoRegistro = ResultadoAuth.Error(ErrorAuth.EMAIL_EN_USO) }
        val vm = AuthViewModel(repo, SavedStateHandle())
        val datos = RegistroUsuario("Ana", "Pérez", "ana@empresa.es", "mi frase de acceso", "mi frase de acceso", "Empresa", "Limpieza")
        vm.editarRegistro(datos); vm.registrar(); runCurrent()
        assertTrue(CampoAuth.EMAIL in vm.ui.value.errores)
        assertEquals(1, vm.ui.value.intentoValidacion)
        assertEquals(datos, vm.ui.value.registro)
    }
    @Test fun dobleToqueLanzaUnaOperacionYLimpiaPassword() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = AuthViewModel(repo, SavedStateHandle())
        vm.editarLogin("a@empresa.es", "contraseña larga")
        vm.iniciarSesion(); vm.iniciarSesion(); runCurrent()
        assertEquals(1, repo.logins)
        assertEquals("", vm.ui.value.password)
        assertFalse(vm.ui.value.ocupado)
        assertNotNull(vm.ui.value.mensaje)
    }
    @Test fun savedStateNoContienePassword() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = AuthViewModel(FakeRepository(), saved)
        vm.editarLogin("a@empresa.es", "secreto")
        assertEquals(setOf("loginEmail"), saved.keys())
        assertEquals("", AuthViewModel(FakeRepository(), saved).ui.value.password)
    }

    @Test fun recuperacionValidaCorreoYEvitaSolicitudesDuplicadas() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = AuthViewModel(repo, SavedStateHandle())
        vm.recuperarPassword(); runCurrent()
        assertEquals(0, repo.solicitudes)
        vm.editarEmailRecuperacion(" ADMIN@Empresa.es ")
        vm.recuperarPassword(); vm.recuperarPassword(); runCurrent()
        assertEquals(1, repo.solicitudes)
        assertEquals("admin@empresa.es", repo.ultimoCorreo)
        assertTrue(vm.ui.value.recuperacionSolicitada)
        vm.recuperarPassword(); runCurrent()
        assertEquals(1, repo.solicitudes)
    }

    @Test fun falloDeRedNoSePresentaComoCorreoEnviadoYPermiteReintentar() = runTest(dispatcher) {
        val repo = FakeRepository().apply { resultadoRecuperacion = ResultadoAuth.Error(ErrorAuth.SIN_CONEXION) }
        val vm = AuthViewModel(repo, SavedStateHandle())
        vm.editarEmailRecuperacion("admin@empresa.es")
        vm.recuperarPassword(); runCurrent()
        assertFalse(vm.ui.value.recuperacionSolicitada)
        assertNotNull(vm.ui.value.mensaje)
        repo.resultadoRecuperacion = ResultadoAuth.Correcto
        vm.recuperarPassword(); runCurrent()
        assertTrue(vm.ui.value.recuperacionSolicitada)
    }

    @Test fun recuperacionConservaCorreoYLimpiaClaveAlNavegar() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = AuthViewModel(FakeRepository(), saved)
        vm.editarLogin("admin@empresa.es", "mi contraseña actual")
        vm.abrirRecuperacion()
        assertEquals("", vm.ui.value.password)
        assertEquals("admin@empresa.es", vm.ui.value.emailRecuperacion)
        assertEquals("admin@empresa.es", AuthViewModel(FakeRepository(), saved).ui.value.emailRecuperacion)
        vm.volverDeRecuperacion()
        assertEquals("admin@empresa.es", vm.ui.value.email)
        assertFalse(saved.keys().any { it.contains("password", ignoreCase = true) })
    }
}
