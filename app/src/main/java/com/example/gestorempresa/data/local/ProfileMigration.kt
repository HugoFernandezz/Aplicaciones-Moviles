package com.example.gestorempresa.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Añade dirección sin perder teléfono, fotografía ni preferencias anteriores. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE perfiles ADD COLUMN direccion TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE perfiles ADD COLUMN telefonoRegion TEXT")
    }
}

/**
 * Versión 5: añade las tablas de Perfil y Ajustes (Miembro 4).
 * Solo crea tablas nuevas, así que los usuarios, empresas y empleados existentes no se tocan.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS perfiles (
            usuarioId INTEGER NOT NULL PRIMARY KEY, telefono TEXT NOT NULL, dni TEXT NOT NULL,
            cargo TEXT NOT NULL, fotoRuta TEXT,
            FOREIGN KEY(usuarioId) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS ajustes (
            usuarioId INTEGER NOT NULL PRIMARY KEY, avisosVencimientos INTEGER NOT NULL,
            diasAntelacion INTEGER NOT NULL, avisosTareas INTEGER NOT NULL, tema TEXT NOT NULL,
            FOREIGN KEY(usuarioId) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
    }
}
