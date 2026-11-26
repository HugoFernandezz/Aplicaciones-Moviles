package com.example.gestorempresa.auth

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gestorempresa.BuildConfig
import com.example.gestorempresa.data.auth.*
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.domain.auth.*
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Comprueba los adaptadores Android reales contra Auth + Firestore Emulator.
 * Dos bases vacías, un solo SDK de instalación: no sustituye la prueba con dos teléfonos.
 */
class FirebaseRepositoryEmulatorTest {
    @Test fun registroYRecuperacionDesdeBaseVaciaUsanElPerfilDelServidor() = runBlocking {
        assumeTrue(BuildConfig.AUTH_EMULATOR_HOST.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val sdk = FirebaseEmailAuthService(context)
        val perfiles = FirestorePerfilCuentaService(sdk)
        val clock = Clock.systemUTC()
        fun repo(db: AppDatabase) = FirebaseAuthRepository(db, RoomAuthRepository(db, PasswordHasher(), clock), sdk, PasswordHasher(), clock, perfiles)
        val a = repo(dbA)
        val b = repo(dbB)
        val data = RegistroUsuario("Ana", "Pérez", "prueba.${System.nanoTime()}@example.invalid",
            "frase para el emulador", "frase para el emulador", "Empresa de integración", "Limpieza")
        try {
            assertEquals(ResultadoAuth.Correcto, a.registrar(data))
            assertEquals(ResultadoAuth.Correcto, a.iniciarSesion(data.email, data.password))
            val before = requireNotNull(a.sesion.first())
            assertEquals(ResultadoAuth.Correcto, a.cerrarSesion())
            assertNull(dbB.authDao().buscarPorEmail(data.email))
            assertEquals(ResultadoAuth.Correcto, b.iniciarSesion(data.email, data.password))
            val after = requireNotNull(b.sesion.first())
            assertEquals(before.usuario.firebaseUid, after.usuario.firebaseUid)
            assertEquals(before.empresa.remoteId, after.empresa.remoteId)
            assertEquals(before.empresa.nombre, after.empresa.nombre)
            assertEquals("", dbB.authDao().buscarPorEmail(data.email)?.passwordHash)
            assertEquals(ResultadoAuth.Correcto, b.recuperarPassword(data.email))
            assertEquals(ResultadoAuth.Correcto, b.cerrarSesion())
            assertNull(b.sesion.first())
        } finally { sdk.salir(); dbA.close(); dbB.close() }
    }
}
