package com.example.gestorempresa.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.auth.*
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.domain.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal class FakeEmailService(val accounts: MutableMap<String, Pair<String, String>> = mutableMapOf()) : EmailAuthService {
    override var configurado = true
    override val identidad = MutableStateFlow<IdentidadRemota?>(null)

    val resetEmails = mutableListOf<String>()
    var errorEnvio: ErrorAuth? = null
    var altasDescartadas = 0
    override suspend fun registrar(email: String, password: String): IdentidadRemota {
        if (email in accounts) throw ErrorServicioAuth(ErrorAuth.EMAIL_EN_USO)
        val uid = "uid-${accounts.size + 1}"
        accounts[email] = uid to password
        return IdentidadRemota(uid, email).also { identidad.value = it }
    }
    override suspend fun ingresar(email: String, password: String): IdentidadRemota {
        val account = accounts[email]
        if (account == null || account.second != password) throw ErrorServicioAuth(ErrorAuth.CREDENCIALES_INVALIDAS)
        return IdentidadRemota(account.first, email).also { identidad.value = it }
    }
    override suspend fun enviarRecuperacion(email: String) {
        errorEnvio?.let { throw ErrorServicioAuth(it) }
        resetEmails += email
    }
    override suspend fun actualizarSesion() { }
    override suspend fun descartarAlta(uid: String) {
        accounts.entries.removeAll { it.value.first == uid }; altasDescartadas++; salir()
    }
    override fun salir() { identidad.value = null }
    fun restablecer(email: String, password: String) {
        accounts[email] = requireNotNull(accounts[email]).first to password
        salir()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FirebaseAuthRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var local: RoomAuthRepository
    private lateinit var remote: FakeEmailService
    private val perfiles = FakePerfilCuentaService()
    private lateinit var repo: FirebaseAuthRepository
    private val clock = MutableClock()
    private val data = RegistroUsuario("Irene", "García", "admin@empresa.es", "mi frase de acceso", "mi frase de acceso", "Mi empresa", "Limpieza")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        local = RoomAuthRepository(db, PasswordHasher(), clock)
        remote = FakeEmailService()
        repo = FirebaseAuthRepository(db, local, remote, PasswordHasher(), clock, perfiles)
    }
    @After fun tearDown() { db.close() }

    @Test fun registroCreaPerfilConUidSinGuardarPasswordNiAbrirSesion() = runBlocking {
        assertEquals(ResultadoAuth.Correcto, repo.registrar(data))
        val profile = requireNotNull(db.authDao().buscarPorEmail(data.email))
        assertNotNull(profile.firebaseUid)
        assertEquals("", profile.passwordHash)
        assertEquals("", profile.passwordSalt)
        assertEquals(0, profile.passwordIterations)
        assertNull(remote.identidad.value)
        assertNull(repo.sesion.first())
    }

    @Test fun nuevaClaveDelProveedorSustituyeLaAnteriorSinCambiarEmpresa() = runBlocking {
        repo.registrar(data); repo.iniciarSesion(data.email, data.password)
        val before = requireNotNull(repo.sesion.first())
        remote.restablecer(data.email, "Nueva frase de acceso")
        assertNull(repo.sesion.first())
        assertEquals(ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS), repo.iniciarSesion(data.email, data.password))
        assertEquals(ResultadoAuth.Correcto, repo.iniciarSesion(data.email, "Nueva frase de acceso"))
        val after = requireNotNull(repo.sesion.first())
        assertEquals(before.usuario.id, after.usuario.id)
        assertEquals(before.empresa.id, after.empresa.id)
        assertEquals(ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO), local.iniciarSesion(data.email, data.password))
    }

    @Test fun migraCuentaAnteriorSoloTrasVerificarSuPasswordYConservaIds() = runBlocking {
        local.registrar(data)
        val old = requireNotNull(db.authDao().buscarPorEmail(data.email))
        assertEquals(ResultadoAuth.Correcto, repo.iniciarSesion(data.email, data.password))
        val linked = requireNotNull(db.authDao().buscarPorEmail(data.email))
        assertEquals(old.id, linked.id)
        assertEquals(old.empresaId, linked.empresaId)
        assertNotNull(linked.firebaseUid)
        assertEquals("", linked.passwordHash)
        assertEquals("", linked.passwordSalt)
        assertEquals(old.empresaId, repo.sesion.first()?.empresa?.id)
    }

    @Test fun unCorreoRemotoCoincidenteNoPermiteRobarUnaCuentaLocal() = runBlocking {
        local.registrar(data)
        remote.registrar(data.email, "otra clave remota")
        remote.salir()
        assertEquals(ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS), repo.iniciarSesion(data.email, "otra clave remota"))
        assertNull(db.authDao().buscarPorEmail(data.email)?.firebaseUid)
        assertNull(repo.sesion.first())
    }

    @Test fun colisionDeCuentaAntiguaNoSobrescribeLaIdentidadRemota() = runBlocking {
        local.registrar(data)
        val identity = remote.registrar(data.email, "otra clave remota")
        remote.salir()
        assertEquals(ResultadoAuth.Error(ErrorAuth.VINCULACION_NECESARIA), repo.iniciarSesion(data.email, data.password))
        assertNull(db.authDao().buscarPorEmail(data.email)?.firebaseUid)
        assertEquals(identity.uid, remote.ingresar(data.email, "otra clave remota").uid)
    }

    @Test fun solicitudNormalizaCorreoSinCambiarPasswordNiSesion() = runBlocking {
        repo.registrar(data); repo.iniciarSesion(data.email, data.password)
        val before = repo.sesion.first()
        assertEquals(ResultadoAuth.Correcto, repo.recuperarPassword(" ADMIN@Empresa.es "))
        assertEquals(listOf(data.email), remote.resetEmails)
        assertEquals(before, repo.sesion.first())
        assertEquals(ResultadoAuth.Correcto, repo.iniciarSesion(data.email, data.password))
        assertEquals(ResultadoAuth.Correcto, repo.recuperarPassword("desconocido@empresa.es"))
    }

    @Test fun falloDeRedOConfiguracionNoSimulaUnEnvio() = runBlocking {
        remote.configurado = false
        assertEquals(ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO), repo.recuperarPassword(data.email))
        assertTrue(remote.resetEmails.isEmpty())
        remote.configurado = true
        remote.errorEnvio = ErrorAuth.SIN_CONEXION
        assertEquals(ResultadoAuth.Error(ErrorAuth.SIN_CONEXION), repo.recuperarPassword(data.email))
        assertTrue(remote.resetEmails.isEmpty())
        assertEquals(ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS), repo.recuperarPassword("correo incorrecto"))
    }

    @Test fun faltaDeConfiguracionConservaAccesoAntiguoPeroNoCreaCuentasLocalesNuevas() = runBlocking {
        local.registrar(data)
        remote.configurado = false
        assertEquals(ResultadoAuth.Correcto, repo.iniciarSesion(data.email, data.password))
        assertNotNull(repo.sesion.first())
        assertEquals(ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO), repo.registrar(data.copy(email = "otra@empresa.es")))
    }

    @Test fun cerrarSesionYRevocacionBloqueanElContenidoAunqueRoomConserveElPerfil() = runBlocking {
        repo.registrar(data); repo.iniciarSesion(data.email, data.password)
        remote.salir()
        assertNull(repo.sesion.first())
        repo.iniciarSesion(data.email, data.password)
        assertEquals(ResultadoAuth.Correcto, repo.cerrarSesion())
        assertNull(repo.sesion.first())
        assertNull(remote.identidad.value)
        assertNotNull(db.authDao().buscarPorEmail(data.email))
    }

    @Test fun identidadRemotaValidaNoSustituyeLosDatosDeEmpresaQueFaltan() = runBlocking {
        remote.registrar(data.email, data.password); remote.salir()
        assertEquals(ResultadoAuth.Error(ErrorAuth.PERFIL_NO_DISPONIBLE), repo.iniciarSesion(data.email, data.password))
        assertNull(remote.identidad.value)
        assertNull(repo.sesion.first())
    }
}
