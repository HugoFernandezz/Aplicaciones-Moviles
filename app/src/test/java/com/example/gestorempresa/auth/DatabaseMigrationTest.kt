package com.example.gestorempresa.auth

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.auth.PasswordHasher
import com.example.gestorempresa.data.auth.RoomAuthRepository
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.MIGRATION_1_2
import com.example.gestorempresa.data.local.MIGRATION_2_3
import com.example.gestorempresa.data.local.MIGRATION_3_4
import com.example.gestorempresa.data.local.MIGRATION_4_5
import com.example.gestorempresa.data.local.MIGRATION_5_6
import com.example.gestorempresa.data.local.MIGRATION_6_7
import com.example.gestorempresa.domain.auth.RegistroUsuario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DatabaseMigrationTest {
    @Test fun migracionSeisASieteConservaFotoTelefonoYDatosDeLaCuenta() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-profile-7-test.db"
        context.deleteDatabase(name)
        val schema = requireNotNull(javaClass.getResourceAsStream("/com.example.gestorempresa.data.local.AppDatabase/6.json"))
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val table = entities.getJSONObject(i)
                val tableName = table.getString("tableName")
                old.execSQL(table.getString("createSql").replace("\u0024{TABLE_NAME}", tableName))
                val indices = table.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}", tableName))
            }
            old.execSQL("INSERT INTO empresas VALUES (7, 'Empresa anterior', 'Limpieza', 1, 'uid-antiguo')")
            old.execSQL("INSERT INTO usuarios VALUES (9, 'Ana', 'Pérez', 'ana@empresa.es', 7, 'ADMIN', '', '', 0, 0, 0, 'uid-antiguo')")
            old.execSQL("INSERT INTO perfiles VALUES (9, '+442079460018', '', 'Gerente', 'foto-local')")
            old.version = 6
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_6_7).allowMainThreadQueries().build()
        try {
            val profile = requireNotNull(migrated.perfilDao().buscarPerfil(9))
            assertEquals("+442079460018", profile.telefono)
            assertEquals("foto-local", profile.fotoRuta)
            assertEquals("", profile.direccion)
            assertNull(profile.telefonoRegion)
            assertEquals("uid-antiguo", migrated.authDao().buscarUsuario(9)?.firebaseUid)
            assertEquals(7, migrated.openHelper.readableDatabase.version)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
    @Test fun migracionCincoASeisConservaPerfilAjustesYSesionVinculada() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-multidevice-test.db"
        context.deleteDatabase(name)
        val schema = requireNotNull(javaClass.getResourceAsStream("/com.example.gestorempresa.data.local.AppDatabase/5.json"))
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val table = entities.getJSONObject(i)
                val tableName = table.getString("tableName")
                old.execSQL(table.getString("createSql").replace("\u0024{TABLE_NAME}", tableName))
                val indices = table.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}", tableName))
            }
            old.execSQL("INSERT INTO empresas VALUES (7, 'Empresa anterior', 'Limpieza', 1)")
            old.execSQL("INSERT INTO usuarios VALUES (9, 'Ana', 'Pérez', 'ana@empresa.es', 7, 'ADMIN', '', '', 0, 0, 0, 'uid-antiguo')")
            old.execSQL("INSERT INTO perfiles VALUES (9, '+34 600123456', '', 'Gerente', 'foto-local')")
            old.execSQL("INSERT INTO ajustes VALUES (9, 1, 7, 0, 'OSCURO')")
            old.execSQL("INSERT INTO sesion VALUES (1, 9, 3000000000000)")
            old.version = 5
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7).allowMainThreadQueries().build()
        try {
            val user = requireNotNull(migrated.authDao().buscarPorEmail("ana@empresa.es"))
            assertEquals(9L, user.id)
            assertEquals(7L, user.empresaId)
            assertEquals("uid-antiguo", user.firebaseUid)
            assertNull(migrated.authDao().buscarEmpresa(7)?.remoteId)
            assertEquals("foto-local", migrated.perfilDao().buscarPerfil(9)?.fotoRuta)
            assertEquals("uid-antiguo", migrated.authDao().observarSesion().first()?.firebaseUid)
            migrated.openHelper.readableDatabase.query("SELECT tema FROM ajustes WHERE usuarioId=9").use {
                assertTrue(it.moveToFirst()); assertEquals("OSCURO", it.getString(0))
            }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun migracionDosATresConservaCuentaEmpresaYSesion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-firebase-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = requireNotNull(javaClass.getResourceAsStream("/com.example.gestorempresa.data.local.AppDatabase/2.json"))
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val table = entities.getJSONObject(i)
                val tableName = table.getString("tableName")
                old.execSQL(table.getString("createSql").replace("\u0024{TABLE_NAME}", tableName))
                val indices = table.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}", tableName))
            }
            old.execSQL("INSERT INTO empresas VALUES (7, 'Mi empresa anterior', 'Limpieza', 1)")
            old.execSQL("INSERT INTO usuarios VALUES (9, 'Persona', 'De prueba', 'antigua@example.invalid', 7, 'ADMIN', 'hash-anterior', 'sal-anterior', 600000, 2, 0)")
            old.execSQL("INSERT INTO sesion VALUES (1, 9, 3000000000000)")
            old.version = 2
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).allowMainThreadQueries().build()
        try {
            val user = requireNotNull(migrated.authDao().buscarPorEmail("antigua@example.invalid"))
            assertEquals(9L, user.id)
            assertEquals(7L, user.empresaId)
            assertEquals("hash-anterior", user.passwordHash)
            assertNull(user.firebaseUid)
            val session = RoomAuthRepository(migrated, PasswordHasher(), MutableClock()).sesion.first()
            assertEquals("Mi empresa anterior", session?.empresa?.nombre)
            assertEquals(7, migrated.openHelper.readableDatabase.version)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun migracionValidaEsquemaYConservaEmpleado() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-auth-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        // Fixture independiente: tablas de los modelos del commit original 9562b20.
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val tables = mapOf(
                "empleados" to "nombre TEXT NOT NULL, apellidos TEXT NOT NULL, dni TEXT NOT NULL, telefono TEXT NOT NULL, email TEXT NOT NULL, oficio TEXT NOT NULL, tipoEmpleado TEXT NOT NULL, salarioBase REAL NOT NULL, lugarResidencia TEXT NOT NULL, direccionPersonal TEXT NOT NULL, fotoUrl TEXT, activo INTEGER NOT NULL",
                "unidades_trabajo" to "titulo TEXT NOT NULL, tipo TEXT NOT NULL, clienteNombre TEXT NOT NULL, direccionUbicacion TEXT NOT NULL, latitud REAL, longitud REAL, estado TEXT NOT NULL, fechaInicio TEXT NOT NULL, fechaFin TEXT",
                "registros_horas" to "unidadTrabajoId INTEGER NOT NULL, empleadoId INTEGER, subcontratadoId INTEGER, fecha TEXT NOT NULL, horasTrabajadas REAL NOT NULL, costeHoraAplicado REAL NOT NULL",
                "subcontratados" to "nombreComercial TEXT NOT NULL, personaContacto TEXT NOT NULL, cifDni TEXT NOT NULL, telefono TEXT NOT NULL, email TEXT NOT NULL, oficio TEXT NOT NULL, tarifaHoraPorDefecto REAL NOT NULL, fotoUrl TEXT, activo INTEGER NOT NULL",
                "proveedores" to "nombreEmpresa TEXT NOT NULL, cif TEXT NOT NULL, telefono TEXT NOT NULL, email TEXT NOT NULL, personaContacto TEXT NOT NULL",
                "materiales" to "proveedorId INTEGER NOT NULL, nombreMaterial TEXT NOT NULL, unidadMedida TEXT NOT NULL, precioUnitario REAL NOT NULL",
                "consumos_materiales" to "unidadTrabajoId INTEGER NOT NULL, materialId INTEGER NOT NULL, cantidad REAL NOT NULL, precioUnitarioAplicado REAL NOT NULL, fecha TEXT NOT NULL"
            )
            tables.forEach { (table, columns) -> old.execSQL("CREATE TABLE $table (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, $columns)") }
            old.execSQL("INSERT INTO empleados VALUES (1, 'Ana', 'López', 'DNI', '600000000', 'ana@ejemplo.es', 'Técnica', 'FIJO', 1500, 'Murcia', 'Calle ejemplo', NULL, 1)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).allowMainThreadQueries().build()
        try {
            // Abrirla obliga a Room a validar todas las columnas, índices y FKs del esquema 3.
            migrated.openHelper.readableDatabase.query("SELECT nombre FROM empleados WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals("Ana", it.getString(0))
            }
            assertEquals(7, migrated.openHelper.readableDatabase.version)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun sesionSeRestauraTrasCerrarYReabrirLaBase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "session-reopen-test.db"
        context.deleteDatabase(name)
        val clock = MutableClock()
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val datos = RegistroUsuario("Irene", "García", "admin@empresa.es", "mi frase de acceso", "mi frase de acceso", "Mi empresa", "Limpieza")
        val repo = RoomAuthRepository(first, PasswordHasher(), clock)
        repo.registrar(datos); repo.iniciarSesion(datos.email, datos.password)
        val expected = requireNotNull(repo.sesion.first())
        first.close()
        val second = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            assertEquals(expected, RoomAuthRepository(second, PasswordHasher(), clock).sesion.first())
        } finally { second.close(); context.deleteDatabase(name) }
    }
}
