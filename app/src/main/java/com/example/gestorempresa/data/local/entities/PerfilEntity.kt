package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Datos personales extra del administrador que no se piden en el Registro.
 * Hay como mucho una fila por usuario (la clave primaria es el propio usuarioId).
 * Si se borra el usuario, se borra también su perfil (CASCADE).
 */
@Entity(
    tableName = "perfiles",
    foreignKeys = [ForeignKey(entity = UsuarioEntity::class, parentColumns = ["id"], childColumns = ["usuarioId"], onDelete = ForeignKey.CASCADE)]
)
data class PerfilEntity(
    @PrimaryKey val usuarioId: Long,
    val telefono: String = "",
    val dni: String = "",
    val cargo: String = "",
    /** Ruta de la foto dentro del almacenamiento interno de la app. Null = sin foto. */
    val fotoRuta: String? = null,
    @ColumnInfo(defaultValue = "''") val direccion: String = "",
    val telefonoRegion: String? = null
)
