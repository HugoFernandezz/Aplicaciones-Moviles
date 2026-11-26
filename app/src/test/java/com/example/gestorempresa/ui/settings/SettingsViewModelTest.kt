package com.example.gestorempresa.ui.settings

import com.example.gestorempresa.domain.auth.ErrorAuth
import com.example.gestorempresa.domain.auth.ResultadoAuth
import com.example.gestorempresa.domain.settings.AjustesApp
import com.example.gestorempresa.domain.settings.SettingsRepository
import com.example.gestorempresa.domain.settings.TemaApp
import com.example.gestorempresa.ui.profile.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** SettingsRepository de prueba: guarda los ajustes en memoria. */
private class FakeSettingsRepository : SettingsRepository {
    val ajustes = MutableStateFlow(AjustesApp())
    override fun observarAjustes(usuarioId: Long) = ajustes
    override suspend fun guardarAjustes(usuarioId: Long, ajustes: AjustesApp) { this.ajustes.value = ajustes }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val auth = FakeAuthRepository()
    private val repositorio = FakeSettingsRepository()
    private lateinit var viewModel: SettingsViewModel

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(auth, repositorio)
    }

    @After fun limpiar() = Dispatchers.resetMain()

    @Test fun muestraLosAjustesPorDefectoYLosDatosDeLaCuenta() = runTest {
        val estado = viewModel.ui.value
        assertEquals(AjustesApp(), estado.ajustes)
        assertEquals("juan@empresa.es", estado.email)
        assertEquals("Construcciones Murcia", estado.empresa)
    }

    @Test fun cadaCambioSeGuardaEnElRepositorio() = runTest {
        viewModel.cambiarTema(TemaApp.OSCURO)
        viewModel.cambiarDiasAntelacion(30)
        viewModel.cambiarAvisosTareas(false)
        assertEquals(AjustesApp(diasAntelacion = 30, avisosTareas = false, tema = TemaApp.OSCURO), repositorio.ajustes.value)
    }

    @Test fun cambioDePasswordInformaDelCorreoEnviado() = runTest {
        viewModel.solicitarCambioPassword()
        assertEquals("Te hemos enviado un correo a juan@empresa.es para cambiar la contraseña.", viewModel.ui.value.mensaje)
    }

    @Test fun cambioDePasswordSinConexionMuestraError() = runTest {
        auth.resultadoRecuperacion = ResultadoAuth.Error(ErrorAuth.SIN_CONEXION)
        viewModel.solicitarCambioPassword()
        assertEquals("No hay conexión a Internet. Inténtalo de nuevo.", viewModel.ui.value.mensaje)
    }
}
