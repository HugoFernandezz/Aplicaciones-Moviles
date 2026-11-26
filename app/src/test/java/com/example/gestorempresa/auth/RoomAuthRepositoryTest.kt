package com.example.gestorempresa.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.auth.*
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.domain.auth.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class MutableClock(var now: Long = 1_800_000_000_000) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoomAuthRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: RoomAuthRepository
    private val clock = MutableClock()
    private val valid = RegistroUsuario("Irene", "García", "admin@empresa.es", "mi frase de acceso", "mi frase de acceso", "Servicios Murcia", "Limpieza")

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomAuthRepository(db, PasswordHasher(), clock)
    }
    @After fun tearDown() { db.close() }

    @Test fun registroAsociaEmpresaSinAbrirSesion() = runBlocking {
        assertEquals(ResultadoAuth.Correcto, repository.registrar(valid))
        assertNull(repository.sesion.first())
        assertEquals(ResultadoAuth.Correcto, repository.iniciarSesion(" ADMIN@Empresa.es ", valid.password))
        val session = requireNotNull(repository.sesion.first())
        assertEquals(session.empresa.id, session.usuario.empresaId)
        assertEquals("Servicios Murcia", session.empresa.nombre)
        assertEquals("ADMIN", session.usuario.rol)
    }
    @Test fun correoDuplicadoNoCreaEmpresaHuerfana() = runBlocking {
        repository.registrar(valid)
        assertEquals(ResultadoAuth.Error(ErrorAuth.EMAIL_EN_USO), repository.registrar(valid.copy(email = "ADMIN@EMPRESA.ES")))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM empresas").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
    @Test fun datosInvalidosNoSeGuardan() = runBlocking {
        assertEquals(ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS), repository.registrar(valid.copy(empresa = "")))
        assertNull(db.authDao().buscarPorEmail(valid.email))
    }
    @Test fun errorGenericoParaCorreoYPassword() = runBlocking {
        repository.registrar(valid)
        val wrong = repository.iniciarSesion(valid.email, "incorrecta")
        val absent = repository.iniciarSesion("nadie@empresa.es", "incorrecta")
        assertEquals(ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS), wrong)
        assertEquals(wrong, absent)
        assertNull(repository.sesion.first())
    }
    @Test fun bloqueoTemporalYCaducidad() = runBlocking {
        repository.registrar(valid)
        repeat(5) { repository.iniciarSesion(valid.email, "incorrecta") }
        assertEquals(ResultadoAuth.Error(ErrorAuth.BLOQUEO_TEMPORAL), repository.iniciarSesion(valid.email, valid.password))
        clock.now += 30_001
        assertEquals(ResultadoAuth.Correcto, repository.iniciarSesion(valid.email, valid.password))
        clock.now += RoomAuthRepository.DURACION_SESION
        assertNull(repository.sesion.first())
    }
    @Test fun logoutBorraSesionPeroMantieneCuenta() = runBlocking {
        repository.registrar(valid); repository.iniciarSesion(valid.email, valid.password)
        assertEquals(ResultadoAuth.Correcto, repository.cerrarSesion())
        assertNull(repository.sesion.first())
        assertNotNull(db.authDao().buscarPorEmail(valid.email))
    }
    @Test fun cuentasConEmpresasDiferentes() = runBlocking {
        repository.registrar(valid)
        repository.iniciarSesion(valid.email, valid.password)
        val first = requireNotNull(repository.sesion.first())
        repository.cerrarSesion()
        repository.registrar(valid.copy(email = "otra@empresa.es", empresa = "Otra empresa"))
        repository.iniciarSesion("otra@empresa.es", valid.password)
        val second = requireNotNull(repository.sesion.first())
        assertNotEquals(first.usuario.id, second.usuario.id)
        assertNotEquals(first.empresa.id, second.empresa.id)
    }
    @Test fun nuevoRepositorioRestauraSesionPersistida() = runBlocking {
        repository.registrar(valid); repository.iniciarSesion(valid.email, valid.password)
        val expected = repository.sesion.first()
        val recreated = RoomAuthRepository(db, PasswordHasher(), clock)
        assertEquals(expected, recreated.sesion.first())
    }
}
