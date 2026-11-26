package com.example.gestorempresa.ui.profile

import com.example.gestorempresa.domain.auth.*
import com.example.gestorempresa.domain.profile.DatosPerfil
import com.example.gestorempresa.domain.profile.PerfilAdministrador
import com.example.gestorempresa.domain.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** AuthRepository de prueba: siempre hay una sesión iniciada. */
class FakeAuthRepository : AuthRepository {
    val empresa = Empresa(10, "Construcciones Murcia", "Construcción")
    override val sesion = MutableStateFlow<SesionUsuario?>(
        SesionUsuario(Usuario(1, "Juan", "Pérez", "juan@empresa.es", 10), empresa, expiraEn = 0)
    )
    var resultadoRecuperacion: ResultadoAuth = ResultadoAuth.Correcto
    override suspend fun registrar(datos: RegistroUsuario) = ResultadoAuth.Correcto
    override suspend fun iniciarSesion(email: String, password: String) = ResultadoAuth.Correcto
    override suspend fun cerrarSesion() = ResultadoAuth.Correcto
    override suspend fun recuperarPassword(email: String) = resultadoRecuperacion
}

/** ProfileRepository de prueba: guarda el perfil en memoria. */
private class FakeProfileRepository(empresa: Empresa) : ProfileRepository {
    val perfil = MutableStateFlow(PerfilAdministrador(1, "Juan", "Pérez", "juan@empresa.es", "ADMIN", "", "", "", null, empresa))
    var guardados = 0
    override fun observarPerfil(usuarioId: Long) = perfil.map { it.takeIf { usuarioId == it.usuarioId } }
    override suspend fun guardarPerfil(usuarioId: Long, empresaId: Long, datos: DatosPerfil) {
        guardados++
        perfil.value = perfil.value.copy(nombre = datos.nombre, telefono = datos.telefono)
    }
    override suspend fun cambiarFoto(usuarioId: Long, uriImagen: String) { perfil.value = perfil.value.copy(fotoRuta = "foto.jpg") }
    override suspend fun eliminarFoto(usuarioId: Long) { perfil.value = perfil.value.copy(fotoRuta = null) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @Test fun salirDeLaCuentaLimpiaFormularioFotoYErrores() = runTest {
        viewModel.empezarEdicion()
        viewModel.editar(viewModel.ui.value.formulario.copy(nombre = "Borrador"))
        viewModel.cambiarFoto("content://foto")
        auth.sesion.value = null
        assertNull(viewModel.ui.value.perfil)
        assertFalse(viewModel.ui.value.editando)
        assertEquals(DatosPerfil(), viewModel.ui.value.formulario)
        assertTrue(viewModel.ui.value.errores.isEmpty())
    }
    @Test fun cambiarUnCampoNoBorraLosErroresDeLosDemas() = runTest {
        viewModel.empezarEdicion()
        viewModel.editar(DatosPerfil())
        viewModel.guardar()
        assertEquals(1, viewModel.ui.value.intentoValidacion)
        viewModel.editar(viewModel.ui.value.formulario.copy(nombre = "Ana"))
        assertFalse(com.example.gestorempresa.domain.profile.CampoPerfil.NOMBRE in viewModel.ui.value.errores)
        assertTrue(com.example.gestorempresa.domain.profile.CampoPerfil.EMPRESA in viewModel.ui.value.errores)
    }
    @Test fun fotoDeUnSelectorAnteriorNoSeAsignaATraCuenta() = runTest {
        viewModel.cambiarFoto("content://foto", usuarioEsperado = 99)
        assertNull(viewModel.ui.value.perfil?.fotoRuta)
    }
    private val auth = FakeAuthRepository()
    private val repositorio = FakeProfileRepository(auth.empresa)
    private lateinit var viewModel: ProfileViewModel

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ProfileViewModel(auth, repositorio)
    }

    @After fun limpiar() = Dispatchers.resetMain()

    @Test fun cargaElPerfilDelUsuarioDeLaSesion() = runTest {
        val estado = viewModel.ui.value
        assertFalse(estado.cargando)
        assertEquals("Juan Pérez", estado.perfil?.nombreCompleto)
    }

    @Test fun editarRellenaElFormularioConLosDatosActuales() = runTest {
        viewModel.empezarEdicion()
        assertTrue(viewModel.ui.value.editando)
        assertEquals("Construcciones Murcia", viewModel.ui.value.formulario.empresaNombre)
    }

    @Test fun noGuardaSiHayErrores() = runTest {
        viewModel.empezarEdicion()
        viewModel.editar(viewModel.ui.value.formulario.copy(nombre = ""))
        viewModel.guardar()
        assertEquals(0, repositorio.guardados)
        assertTrue(viewModel.ui.value.errores.isNotEmpty())
    }

    @Test fun guardarCorrectoCierraElFormularioYMuestraMensaje() = runTest {
        viewModel.empezarEdicion()
        viewModel.editar(viewModel.ui.value.formulario.copy(nombre = "Juana", telefono = "600123456"))
        viewModel.guardar()
        val estado = viewModel.ui.value
        assertEquals(1, repositorio.guardados)
        assertFalse(estado.editando)
        assertEquals("Juana", estado.perfil?.nombre)
        assertEquals("Perfil actualizado.", estado.mensaje)
    }

    @Test fun cambiarYEliminarFoto() = runTest {
        viewModel.cambiarFoto("content://galeria/foto.jpg")
        assertEquals("foto.jpg", viewModel.ui.value.perfil?.fotoRuta)
        viewModel.eliminarFoto()
        assertNull(viewModel.ui.value.perfil?.fotoRuta)
    }
}
