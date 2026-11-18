package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Preferencias de la aplicación de cada usuario (una fila por usuario).
 * El tema se guarda como texto con el nombre del enum TemaApp ("SISTEMA", "CLARO", "OSCURO").
 */
@Entity(
    tableName = "ajustes",
    foreignKeys = [ForeignKey(entity = UsuarioEntity::class, parentColumns = ["id"], childColumns = ["usuarioId"], onDelete = ForeignKey.CASCADE)]
)
data class AjustesEntity(
    @PrimaryKey val usuarioId: Long,
    val avisosVencimientos: Boolean,
    val diasAntelacion: Int,
    val avisosTareas: Boolean,
    val tema: String
)
