package com.example.gestorempresa.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acceso a los Ajustes del usuario.
 * Otros módulos (por ejemplo, avisos de vencimientos) pueden leer las preferencias
 * inyectando esta interfaz y llamando a [observarAjustes].
 */
interface SettingsRepository {

    /** Emite los ajustes del usuario; si nunca los ha guardado, emite los valores por defecto. */
    fun observarAjustes(usuarioId: Long): Flow<AjustesApp>

    suspend fun guardarAjustes(usuarioId: Long, ajustes: AjustesApp)
}
