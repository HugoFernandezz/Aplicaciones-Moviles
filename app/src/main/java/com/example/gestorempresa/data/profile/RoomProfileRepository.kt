package com.example.gestorempresa.data.profile

import androidx.room.withTransaction
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.PerfilEntity
import com.example.gestorempresa.domain.profile.DatosPerfil
import com.example.gestorempresa.domain.profile.PerfilAdministrador
import com.example.gestorempresa.domain.profile.PerfilValidator
import com.example.gestorempresa.domain.profile.ProfileRepository
import com.example.gestorempresa.domain.auth.PerfilCuentaService
import com.example.gestorempresa.domain.auth.PerfilCuentaRemoto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementación de [ProfileRepository] con Room. */
@Singleton
class RoomProfileRepository @Inject constructor(
    private val db: AppDatabase,
    private val fotos: FotoPerfilStorage,
    private val cuentas: PerfilCuentaService
) : ProfileRepository {

    private val dao = db.perfilDao()

    override fun observarPerfil(usuarioId: Long): Flow<PerfilAdministrador?> =
        dao.observarPerfil(usuarioId).map { row -> row?.toDomain()?.let { perfil ->
            perfil.copy(fotoRuta = perfil.fotoRuta?.takeIf { fotos.pertenece(usuarioId, it) })
        } }

    override suspend fun guardarPerfil(usuarioId: Long, empresaId: Long, datos: DatosPerfil) {
        val limpio = PerfilValidator.normalizar(datos)
        require(PerfilValidator.validar(limpio).isEmpty()) { "Datos de perfil no válidos" }

        val usuario = requireNotNull(db.authDao().buscarUsuario(usuarioId))
        require(usuario.empresaId == empresaId) { "Empresa ajena al usuario" }
        val empresa = requireNotNull(db.authDao().buscarEmpresa(empresaId))
        usuario.firebaseUid?.let { uid ->
            // Confirma el guardado compartido antes de anunciar éxito en Perfil.
            cuentas.actualizar(PerfilCuentaRemoto(uid, limpio.nombre, limpio.apellidos,
                empresa.remoteId ?: uid, limpio.empresaNombre, limpio.sector, empresa.creadaEn,
                limpio.telefono, limpio.dni, limpio.cargo, limpio.direccion, limpio.telefonoRegion))
        }

        // Transacción: o se guardan las tres tablas, o no se guarda ninguna.
        db.withTransaction {
            dao.actualizarUsuario(usuarioId, limpio.nombre, limpio.apellidos)
            dao.actualizarEmpresa(empresaId, limpio.empresaNombre, limpio.sector)
            val actual = dao.buscarPerfil(usuarioId) ?: PerfilEntity(usuarioId)
            dao.guardarPerfil(actual.copy(telefono = limpio.telefono, dni = limpio.dni, cargo = limpio.cargo, direccion = limpio.direccion, telefonoRegion = limpio.telefonoRegion))
        }
    }

    override suspend fun cambiarFoto(usuarioId: Long, uriImagen: String) {
        requireNotNull(db.authDao().buscarUsuario(usuarioId)) { "Usuario no disponible" }
        val nuevaRuta = fotos.guardar(usuarioId, uriImagen)
        val rutaAnterior = try { actualizarRutaFoto(usuarioId, nuevaRuta) }
        catch (e: Exception) { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { fotos.borrar(nuevaRuta) }; throw e }
        if (rutaAnterior != null && fotos.pertenece(usuarioId, rutaAnterior)) fotos.borrar(rutaAnterior)
    }

    override suspend fun eliminarFoto(usuarioId: Long) {
        val rutaAnterior = actualizarRutaFoto(usuarioId, null)
        if (rutaAnterior != null && fotos.pertenece(usuarioId, rutaAnterior)) fotos.borrar(rutaAnterior)
    }

    /** Cambia la ruta de la foto en la base de datos y devuelve la ruta que había antes. */
    private suspend fun actualizarRutaFoto(usuarioId: Long, ruta: String?): String? = db.withTransaction {
        requireNotNull(db.authDao().buscarUsuario(usuarioId)) { "Usuario no disponible" }
        val actual = dao.buscarPerfil(usuarioId) ?: PerfilEntity(usuarioId)
        dao.guardarPerfil(actual.copy(fotoRuta = ruta))
        actual.fotoRuta
    }
}
