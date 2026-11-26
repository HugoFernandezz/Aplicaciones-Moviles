package com.example.gestorempresa.data.profile

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.MIGRATION_4_5
import com.example.gestorempresa.data.local.MIGRATION_5_6
import com.example.gestorempresa.data.local.MIGRATION_6_7
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import com.example.gestorempresa.auth.FakePerfilCuentaService
import com.example.gestorempresa.data.local.entities.EmpresaEntity
import com.example.gestorempresa.data.local.entities.UsuarioEntity
import com.example.gestorempresa.data.settings.RoomSettingsRepository
import com.example.gestorempresa.domain.profile.DatosPerfil
import com.example.gestorempresa.domain.profile.toDatosPerfil
import com.example.gestorempresa.domain.settings.AjustesApp
import com.example.gestorempresa.domain.settings.TemaApp
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileRepositoryTest {
    @Test fun cerrarYReabrirLaBaseConservaFotoDatosDireccionYPais() = runBlocking {
        val name = "perfil-persistente-test.db"
        context.deleteDatabase(name)
        val source = File(context.cacheDir, "perfil-persistente.png")
        source.outputStream().use { Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val sourceUri = Uri.parse("content://galeria/perfil-persistente.png")
        shadowOf(context.contentResolver).registerInputStream(sourceUri, ByteArrayInputStream(source.readBytes()))
        val storage = FotoPerfilStorage(context)
        var photo: String? = null
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val company = first.authDao().insertarEmpresa(EmpresaEntity(nombre = "Empresa", sector = "Limpieza", creadaEn = 0))
        val user = first.authDao().insertarUsuario(UsuarioEntity(nombre = "Ana", apellidos = "Pérez", email = "persistente@empresa.es",
            empresaId = company, passwordHash = "", passwordSalt = "", passwordIterations = 0))
        try {
            val repo = RoomProfileRepository(first, storage, FakePerfilCuentaService())
            repo.guardarPerfil(user, company, DatosPerfil("Andrea", "Pérez", "32123456", "", "Gerente", "Empresa nueva", "Limpieza",
                direccion = "Calle Mayor 10", telefonoRegion = "DK"))
            repo.cambiarFoto(user, sourceUri.toString())
            photo = repo.observarPerfil(user).first()!!.fotoRuta
        } finally { first.close() }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val p = RoomProfileRepository(reopened, storage, FakePerfilCuentaService()).observarPerfil(user).first()!!
            assertEquals("Andrea", p.nombre)
            assertEquals("+4532123456", p.telefono)
            assertEquals("DK", p.telefonoRegion)
            assertEquals("Calle Mayor 10", p.direccion)
            assertEquals("Empresa nueva", p.empresa.nombre)
            assertEquals(photo, p.fotoRuta)
            assertTrue(File(requireNotNull(p.fotoRuta)).isFile)
        } finally { reopened.close(); context.deleteDatabase(name); source.delete(); storage.borrar(photo) }
    }
    @Test fun guardarDireccionConservaTelefonoInternacionalYRegion() = runBlocking {
        val (user, company) = registrarAdmin()
        perfiles.guardarPerfil(user, company, DatosPerfil("Ana", "Pérez", "020 7946 0018", "", "", "Empresa", "Limpieza",
            direccion = " Calle Mayor 1 ", telefonoRegion = "GB"))
        val saved = requireNotNull(perfiles.observarPerfil(user).first())
        assertEquals("+442079460018", saved.telefono)
        assertEquals("GB", saved.telefonoRegion)
        assertEquals("Calle Mayor 1", saved.direccion)
        perfiles.guardarPerfil(user, company, saved.toDatosPerfil().copy(nombre = "Andrea"))
        assertEquals("+442079460018", perfiles.observarPerfil(user).first()?.telefono)
        assertEquals("Calle Mayor 1", perfiles.observarPerfil(user).first()?.direccion)
    }
    @Test fun paisSinNumeroSeConservaAlVolverAEditar() = runBlocking {
        val (user, company) = registrarAdmin()
        perfiles.guardarPerfil(user, company, DatosPerfil("Ana", "Pérez", empresaNombre = "Empresa", sector = "Limpieza", telefonoRegion = "DK"))
        assertEquals("DK", perfiles.observarPerfil(user).first()?.toDatosPerfil()?.telefonoRegion)
        assertEquals("", perfiles.observarPerfil(user).first()?.telefono)
    }
    @Test fun noActualizaLaEmpresaDeOtroUsuario() = runBlocking {
        val (user, _) = registrarAdmin()
        val other = db.authDao().insertarEmpresa(EmpresaEntity(nombre = "Ajena", sector = "Limpieza", creadaEn = 0))
        try {
            perfiles.guardarPerfil(user, other, DatosPerfil("Ana", "Pérez", empresaNombre = "Cambio", sector = "Limpieza"))
            fail("Debe rechazar una empresa que no pertenece al usuario")
        } catch (_: IllegalArgumentException) { }
        assertEquals("Ajena", db.authDao().buscarEmpresa(other)?.nombre)
    }
    @Test fun unaRutaDeFotoDeOtraCuentaNoSeMuestra() = runBlocking {
        val (user, _) = registrarAdmin()
        db.perfilDao().guardarPerfil(com.example.gestorempresa.data.local.entities.PerfilEntity(user,
            fotoRuta = File(context.filesDir, "fotos_perfil/usuario_999/foto.jpg").path))
        assertNull(perfiles.observarPerfil(user).first()?.fotoRuta)
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val perfiles = RoomProfileRepository(db, FotoPerfilStorage(context), FakePerfilCuentaService())
    private val ajustes = RoomSettingsRepository(db)

    @After fun cerrar() = db.close()

    /** Crea un administrador como lo haría el Registro y devuelve (usuarioId, empresaId). */
    private suspend fun registrarAdmin(): Pair<Long, Long> {
        val empresaId = db.authDao().insertarEmpresa(EmpresaEntity(nombre = "Construcciones Murcia", sector = "Construcción", creadaEn = 0))
        val usuarioId = db.authDao().insertarUsuario(UsuarioEntity(nombre = "Juan", apellidos = "Pérez", email = "juan@empresa.es",
            empresaId = empresaId, passwordHash = "", passwordSalt = "", passwordIterations = 0))
        return usuarioId to empresaId
    }

    @Test fun usuarioRecienRegistradoTienePerfilVacioYAjustesPorDefecto() = runBlocking {
        val (usuarioId, _) = registrarAdmin()
        val perfil = perfiles.observarPerfil(usuarioId).first()!!
        assertEquals("Juan Pérez", perfil.nombreCompleto)
        assertEquals("JP", perfil.iniciales)
        assertEquals("", perfil.telefono)
        assertNull(perfil.fotoRuta)
        assertEquals(AjustesApp(), ajustes.observarAjustes(usuarioId).first())
    }

    @Test fun guardarPerfilActualizaUsuarioEmpresaYDatosPersonales() = runBlocking {
        val (usuarioId, empresaId) = registrarAdmin()
        perfiles.guardarPerfil(usuarioId, empresaId, DatosPerfil("Juana", "Pérez Ruiz", "600123456", "12345678z", "Gerente", "Limpiezas Murcia", "Limpieza"))
        val perfil = perfiles.observarPerfil(usuarioId).first()!!
        assertEquals("Juana Pérez Ruiz", perfil.nombreCompleto)
        assertEquals("12345678Z", perfil.dni)
        assertEquals("Gerente", perfil.cargo)
        assertEquals("Limpiezas Murcia", perfil.empresa.nombre)
        assertEquals("Limpieza", perfil.empresa.sector)
    }

    @Test fun cambiarFotoCopiaLaImagenYBorrarlaVuelveAlAvatar() = runBlocking {
        val (usuarioId, _) = registrarAdmin()
        // Simula la imagen que devolvería la galería del móvil.
        val imagen = Uri.parse("content://galeria/foto.jpg")
        val bytes = ByteArrayOutputStream().also { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        shadowOf(context.contentResolver).registerInputStream(imagen, ByteArrayInputStream(bytes))

        perfiles.cambiarFoto(usuarioId, imagen.toString())
        val ruta = perfiles.observarPerfil(usuarioId).first()!!.fotoRuta!!
        assertEquals(8, BitmapFactory.decodeFile(ruta).width)
        assertTrue(File(ruta).isFile)

        perfiles.eliminarFoto(usuarioId)
        assertNull(perfiles.observarPerfil(usuarioId).first()!!.fotoRuta)
        assertFalse(File(ruta).exists())
    }

    @Test fun guardaYRecuperaAjustes() = runBlocking {
        val (usuarioId, _) = registrarAdmin()
        val nuevos = AjustesApp(avisosVencimientos = false, diasAntelacion = 30, avisosTareas = false, tema = TemaApp.OSCURO)
        ajustes.guardarAjustes(usuarioId, nuevos)
        assertEquals(nuevos, ajustes.observarAjustes(usuarioId).first())
    }

    @Test fun migracionCuatroACincoConservaUsuarios() = runBlocking {
        // Crea una base de datos con el esquema de la versión 4 (antes de Perfil y Ajustes).
        val nombre = "migracion_perfil_test.db"
        context.deleteDatabase(nombre)
        val schema = javaClass.classLoader!!.getResourceAsStream("com.example.gestorempresa.data.local.AppDatabase/4.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(nombre, Context.MODE_PRIVATE, null).use { old ->
            val tablas = schema.getJSONArray("entities")
            for (i in 0 until tablas.length()) {
                val tabla = tablas.getJSONObject(i)
                old.execSQL(tabla.getString("createSql").replace("\${TABLE_NAME}", tabla.getString("tableName")))
                val indices = tabla.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tabla.getString("tableName")))
                }
            }
            val consultas = schema.getJSONArray("setupQueries")
            for (i in 0 until consultas.length()) old.execSQL(consultas.getString(i))
            old.execSQL("INSERT INTO empresas (id,nombre,sector,creadaEn) VALUES (1,'Empresa conservada','Reformas',0)")
            old.execSQL("INSERT INTO usuarios (id,nombre,apellidos,email,empresaId,rol,passwordHash,passwordSalt,passwordIterations,intentosFallidos,bloqueoHasta,firebaseUid) VALUES (5,'Ana','García','ana@empresa.es',1,'ADMIN','','',0,0,0,NULL)")
            old.version = 4
        }

        // Room comprueba al abrir que las tablas creadas por la migración coinciden con las entidades.
        val migrada = Room.databaseBuilder(context, AppDatabase::class.java, nombre).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).allowMainThreadQueries().build()
        try {
            val perfil = RoomProfileRepository(migrada, FotoPerfilStorage(context), FakePerfilCuentaService()).observarPerfil(5).first()!!
            assertEquals("Ana García", perfil.nombreCompleto)
            assertEquals("Empresa conservada", perfil.empresa.nombre)
        } finally { migrada.close() }
    }
}
