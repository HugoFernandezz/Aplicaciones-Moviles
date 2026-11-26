package com.example.gestorempresa.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.auth.*
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.EmpresaEntity
import com.example.gestorempresa.data.profile.FotoPerfilStorage
import com.example.gestorempresa.data.profile.RoomProfileRepository
import com.example.gestorempresa.domain.auth.*
import com.example.gestorempresa.domain.profile.DatosPerfil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MultidispositivoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val clock = MutableClock()
    private val cuentas = mutableMapOf<String, Pair<String, String>>()
    private val sdkA = FakeEmailService(cuentas)
    private val sdkB = FakeEmailService(cuentas)
    private val nube = FakePerfilCuentaService()
    private fun repo(db: AppDatabase, sdk: FakeEmailService) =
        FirebaseAuthRepository(db, RoomAuthRepository(db, PasswordHasher(), clock), sdk, PasswordHasher(), clock, nube)
    private val a = repo(dbA, sdkA)
    private val b = repo(dbB, sdkB)
    private val datos = RegistroUsuario("Ana", "Pérez", "ana@empresa.es", "mi frase de acceso", "mi frase de acceso", "Empresa", "Limpieza")
    @After fun cerrar() { dbA.close(); dbB.close() }

    @Test fun dispositivoNuevoRecuperaCuentaYEmpresaSinDuplicarAlta() = runBlocking {
        assertEquals(ResultadoAuth.Correcto, a.registrar(datos))
        dbB.authDao().insertarEmpresa(EmpresaEntity(id = 42, nombre = "Otra empresa", sector = "Otro", creadaEn = 0))
        assertEquals(ResultadoAuth.Correcto, a.iniciarSesion(datos.email, datos.password))
        assertEquals(ResultadoAuth.Correcto, b.iniciarSesion(datos.email, datos.password))
        val sa = requireNotNull(a.sesion.first())
        val sb = requireNotNull(b.sesion.first())
        assertEquals(sa.usuario.firebaseUid, sb.usuario.firebaseUid)
        assertEquals(sa.empresa.remoteId, sb.empresa.remoteId)
        assertEquals(sa.empresa.nombre, sb.empresa.nombre)
        assertNotEquals(sa.empresa.id, sb.empresa.id) // los IDs locales no identifican cuentas remotas
        repeat(2) { assertEquals(ResultadoAuth.Correcto, b.iniciarSesion(datos.email, datos.password)) }
        assertEquals(sb.usuario.id, b.sesion.first()?.usuario?.id)
        assertEquals(sb.empresa.id, b.sesion.first()?.empresa?.id)
        assertEquals(1, cuentas.size)
        assertEquals(1, nube.creaciones)
        assertEquals("", dbB.authDao().buscarPorEmail(datos.email)?.passwordHash)
    }

    @Test fun logoutDeUnDispositivoConservaLaSesionDelOtro() = runBlocking {
        a.registrar(datos); a.iniciarSesion(datos.email, datos.password); b.iniciarSesion(datos.email, datos.password)
        assertEquals(ResultadoAuth.Correcto, b.cerrarSesion())
        assertNull(b.sesion.first())
        assertNotNull(a.sesion.first())
        assertNotNull(sdkA.identidad.value)
        assertNotNull(dbB.authDao().buscarPorEmail(datos.email))
    }

    @Test fun claveRestablecidaSirveEnElOtroTelefonoSinCrearCuenta() = runBlocking {
        a.registrar(datos); b.iniciarSesion(datos.email, datos.password); b.cerrarSesion()
        sdkA.restablecer(datos.email, "nueva frase segura")
        assertEquals(ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS), b.iniciarSesion(datos.email, datos.password))
        assertEquals(ResultadoAuth.Correcto, b.iniciarSesion(datos.email, "nueva frase segura"))
        assertEquals(1, cuentas.size)
    }

    @Test fun sesionFirebaseNoCaducaArbitrariamenteALosSieteDias() = runBlocking {
        a.registrar(datos); a.iniciarSesion(datos.email, datos.password)
        clock.now += 8L * 24 * 60 * 60 * 1000
        assertNotNull(repo(dbA, sdkA).sesion.first())
        sdkA.salir() // simula que el SDK detecta revocación
        assertNull(a.sesion.first())
    }

    @Test fun errorDeRedAlRecuperarPerfilNoSeConfundeConClaveIncorrecta() = runBlocking {
        a.registrar(datos)
        nube.error = ErrorAuth.SIN_CONEXION
        assertEquals(ResultadoAuth.Error(ErrorAuth.SIN_CONEXION), b.iniciarSesion(datos.email, datos.password))
        assertNull(b.sesion.first())
        assertNull(sdkB.identidad.value)
        nube.error = null
        assertEquals(ResultadoAuth.Correcto, b.iniciarSesion(datos.email, datos.password))
    }

    @Test fun altaConFalloDeSincronizacionSeCompletaConLoginSinDuplicados() = runBlocking {
        nube.error = ErrorAuth.SIN_CONEXION
        assertEquals(ResultadoAuth.Error(ErrorAuth.PERFIL_SINCRONIZACION_PENDIENTE), a.registrar(datos))
        assertEquals(1, cuentas.size)
        assertNull(a.sesion.first())
        assertNotNull(dbA.authDao().buscarPorEmail(datos.email))
        nube.error = null
        assertEquals(ResultadoAuth.Correcto, a.iniciarSesion(datos.email, datos.password))
        assertEquals(ResultadoAuth.Correcto, b.iniciarSesion(datos.email, datos.password))
        assertEquals(1, cuentas.size)
    }

    @Test fun cuentaAnteriorPublicaUnaVezYConservaIdentificadoresLocales() = runBlocking {
        RoomAuthRepository(dbA, PasswordHasher(), clock).registrar(datos)
        val anterior = requireNotNull(dbA.authDao().buscarPorEmail(datos.email))
        assertEquals(ResultadoAuth.Correcto, a.iniciarSesion(datos.email, datos.password))
        assertEquals(anterior.id, a.sesion.first()?.usuario?.id)
        assertEquals(anterior.empresaId, a.sesion.first()?.empresa?.id)
        assertEquals(ResultadoAuth.Correcto, b.iniciarSesion(datos.email, datos.password))
        assertEquals(1, nube.creaciones)
    }

    @Test fun perfilEditadoSeRecuperaEnElOtroDispositivoYNoLoSobrescribeLaCopiaAntigua() = runBlocking {
        a.registrar(datos); a.iniciarSesion(datos.email, datos.password); b.iniciarSesion(datos.email, datos.password)
        val sa = requireNotNull(a.sesion.first())
        val perfiles = RoomProfileRepository(dbA, FotoPerfilStorage(context), nube)
        perfiles.guardarPerfil(sa.usuario.id, sa.empresa.id, DatosPerfil("Andrea", "Pérez", "+34 600123456", "", "Gerente", "Empresa nueva", "Reformas"))
        b.cerrarSesion(); b.iniciarSesion(datos.email, datos.password)
        assertEquals("Andrea", b.sesion.first()?.usuario?.nombre)
        assertEquals("Empresa nueva", b.sesion.first()?.empresa?.nombre)
        assertEquals("+34600123456", dbB.perfilDao().buscarPerfil(requireNotNull(b.sesion.first()).usuario.id)?.telefono)
        assertEquals(sa.empresa.remoteId, b.sesion.first()?.empresa?.remoteId)
        assertEquals(1, nube.creaciones)
    }

    @Test fun falloAlGuardarPerfilNoAnunciaNiGuardaCambiosSoloLocales() = runBlocking {
        a.registrar(datos); a.iniciarSesion(datos.email, datos.password)
        val sa = requireNotNull(a.sesion.first())
        nube.error = ErrorAuth.SIN_CONEXION
        try {
            RoomProfileRepository(dbA, FotoPerfilStorage(context), nube).guardarPerfil(sa.usuario.id, sa.empresa.id,
                DatosPerfil(nombre = "Otro", apellidos = "Nombre", empresaNombre = "Otra", sector = "Otro"))
            fail("Debe comunicar el fallo")
        } catch (e: ErrorServicioAuth) { assertEquals(ErrorAuth.SIN_CONEXION, e.tipo) }
        assertEquals("Ana", a.sesion.first()?.usuario?.nombre)
    }
}
