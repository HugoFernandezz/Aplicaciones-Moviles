package com.example.gestorempresa.domain.profile

import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acceso a los datos del Perfil.
 * La UI solo conoce esta interfaz; la implementación con Room está en `data/profile`.
 */
interface ProfileRepository {

    /** Emite el perfil cada vez que cambia en la base de datos (null si el usuario no existe). */
    fun observarPerfil(usuarioId: Long): Flow<PerfilAdministrador?>

    /** Guarda los datos personales y los de la empresa en una sola operación. */
    suspend fun guardarPerfil(usuarioId: Long, empresaId: Long, datos: DatosPerfil)

    /** Copia la imagen elegida (content://...) al almacenamiento interno y la asocia al perfil. */
    suspend fun cambiarFoto(usuarioId: Long, uriImagen: String)

    /** Elimina la foto y vuelve a mostrarse el avatar por defecto. */
    suspend fun eliminarFoto(usuarioId: Long)
}
