package com.example.gestorempresa.data.settings

import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.AjustesEntity
import com.example.gestorempresa.domain.settings.AjustesApp
import com.example.gestorempresa.domain.settings.SettingsRepository
import com.example.gestorempresa.domain.settings.TemaApp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementación de [SettingsRepository] con Room. */
@Singleton
class RoomSettingsRepository @Inject constructor(db: AppDatabase) : SettingsRepository {

    private val dao = db.ajustesDao()

    override fun observarAjustes(usuarioId: Long): Flow<AjustesApp> =
        // Si el usuario aún no tiene fila guardada, se usan los valores por defecto.
        dao.observarAjustes(usuarioId).map { it?.toDomain() ?: AjustesApp() }

    override suspend fun guardarAjustes(usuarioId: Long, ajustes: AjustesApp) {
        require(ajustes.diasAntelacion in AjustesApp.opcionesAntelacion) { "Antelación no válida" }
        dao.guardarAjustes(ajustes.toEntity(usuarioId))
    }
}

// --- Conversión entre la tabla de Room y el modelo de dominio ---

private fun AjustesEntity.toDomain() = AjustesApp(
    avisosVencimientos = avisosVencimientos,
    diasAntelacion = diasAntelacion,
    avisosTareas = avisosTareas,
    // Si el texto guardado no es un tema conocido, se usa el del sistema.
    tema = TemaApp.entries.firstOrNull { it.name == tema } ?: TemaApp.SISTEMA
)

private fun AjustesApp.toEntity(usuarioId: Long) = AjustesEntity(
    usuarioId = usuarioId,
    avisosVencimientos = avisosVencimientos,
    diasAntelacion = diasAntelacion,
    avisosTareas = avisosTareas,
    tema = tema.name
)
