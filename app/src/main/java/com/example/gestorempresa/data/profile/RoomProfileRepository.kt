package com.example.gestorempresa.data.profile

import androidx.room.withTransaction
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.PerfilEntity
import com.example.gestorempresa.domain.profile.DatosPerfil
import com.example.gestorempresa.domain.profile.PerfilAdministrador
import com.example.gestorempresa.domain.profile.PerfilValidator
import com.example.gestorempresa.domain.profile.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementación de [ProfileRepository] con Room. */
@Singleton
class RoomProfileRepository @Inject constructor(
    private val db: AppDatabase,
    private val fotos: FotoPerfilStorage
) : ProfileRepository {

    private val dao = db.perfilDao()

    override fun observarPerfil(usuarioId: Long): Flow<PerfilAdministrador?> =
        dao.observarPerfil(usuarioId).map { it?.toDomain() }

    override suspend fun guardarPerfil(usuarioId: Long, empresaId: Long, datos: DatosPerfil) {
        val limpio = PerfilValidator.normalizar(datos)
        require(PerfilValidator.validar(limpio).isEmpty()) { "Datos de perfil no válidos" }

        // Transacción: o se guardan las tres tablas, o no se guarda ninguna.
        db.withTransaction {
            dao.actualizarUsuario(usuarioId, limpio.nombre, limpio.apellidos)
            dao.actualizarEmpresa(empresaId, limpio.empresaNombre, limpio.sector)
            val actual = dao.buscarPerfil(usuarioId) ?: PerfilEntity(usuarioId)
            dao.guardarPerfil(actual.copy(telefono = limpio.telefono, dni = limpio.dni, cargo = limpio.cargo))
        }
    }

    override suspend fun cambiarFoto(usuarioId: Long, uriImagen: String) {
        val nuevaRuta = fotos.guardar(usuarioId, uriImagen)
        val rutaAnterior = actualizarRutaFoto(usuarioId, nuevaRuta)
        fotos.borrar(rutaAnterior)
    }

    override suspend fun eliminarFoto(usuarioId: Long) {
        val rutaAnterior = actualizarRutaFoto(usuarioId, null)
        fotos.borrar(rutaAnterior)
    }

    /** Cambia la ruta de la foto en la base de datos y devuelve la ruta que había antes. */
    private suspend fun actualizarRutaFoto(usuarioId: Long, ruta: String?): String? = db.withTransaction {
        val actual = dao.buscarPerfil(usuarioId) ?: PerfilEntity(usuarioId)
        dao.guardarPerfil(actual.copy(fotoRuta = ruta))
        actual.fotoRuta
    }
}
