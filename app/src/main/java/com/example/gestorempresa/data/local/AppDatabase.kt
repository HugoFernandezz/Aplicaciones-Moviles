package com.example.gestorempresa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.gestorempresa.data.local.dao.EmpleadoDao
import com.example.gestorempresa.data.local.dao.AuthDao
import com.example.gestorempresa.data.local.dao.AjustesDao
import com.example.gestorempresa.data.local.dao.PerfilDao
import com.example.gestorempresa.data.local.entities.*

@Database(
    entities = [EmpleadoEntity::class, UnidadTrabajoEntity::class,
                RegistroHorasEntity::class, SubcontratadoEntity::class,
                ProveedorEntity::class, MaterialEntity::class,
                ConsumoMaterialEntity::class, EmpresaEntity::class,
                UsuarioEntity::class, SesionEntity::class,
                PerfilEntity::class, AjustesEntity::class
               ],
    version = 7,
    exportSchema = true
)

abstract class AppDatabase : RoomDatabase() {

    abstract fun empleadoDao(): EmpleadoDao
    abstract fun authDao(): AuthDao
    abstract fun perfilDao(): PerfilDao
    abstract fun ajustesDao(): AjustesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gestor_empresa_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
