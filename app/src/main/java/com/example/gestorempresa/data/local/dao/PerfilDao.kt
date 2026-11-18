package com.example.gestorempresa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.gestorempresa.data.local.entities.PerfilEntity
import com.example.gestorempresa.domain.auth.Empresa
import com.example.gestorempresa.domain.profile.PerfilAdministrador
import kotlinx.coroutines.flow.Flow

/** Resultado de la consulta que une usuario + empresa + perfil en una sola fila. */
data class PerfilRelacion(
    val usuarioId: Long, val nombre: String, val apellidos: String, val email: String, val rol: String,
    val telefono: String, val dni: String, val cargo: String, val fotoRuta: String?,
    val empresaId: Long, val nombreEmpresa: String, val sector: String
) {
    fun toDomain() = PerfilAdministrador(
        usuarioId = usuarioId, nombre = nombre, apellidos = apellidos, email = email, rol = rol,
        telefono = telefono, dni = dni, cargo = cargo, fotoRuta = fotoRuta,
        empresa = Empresa(empresaId, nombreEmpresa, sector)
    )
}

@Dao
interface PerfilDao {

    /**
     * LEFT JOIN con `perfiles` porque un usuario recién registrado aún no tiene fila de perfil;
     * en ese caso COALESCE devuelve "" en lugar de null.
     */
    @Query("""
        SELECT u.id AS usuarioId, u.nombre, u.apellidos, u.email, u.rol,
               COALESCE(p.telefono, '') AS telefono, COALESCE(p.dni, '') AS dni,
               COALESCE(p.cargo, '') AS cargo, p.fotoRuta,
               e.id AS empresaId, e.nombre AS nombreEmpresa, e.sector
        FROM usuarios u
        JOIN empresas e ON u.empresaId = e.id
        LEFT JOIN perfiles p ON p.usuarioId = u.id
        WHERE u.id = :usuarioId
    """)
    fun observarPerfil(usuarioId: Long): Flow<PerfilRelacion?>

    @Query("SELECT * FROM perfiles WHERE usuarioId = :usuarioId")
    suspend fun buscarPerfil(usuarioId: Long): PerfilEntity?

    /** Inserta la fila si no existe o la actualiza si ya existe. */
    @Upsert
    suspend fun guardarPerfil(perfil: PerfilEntity)

    @Query("UPDATE usuarios SET nombre = :nombre, apellidos = :apellidos WHERE id = :usuarioId")
    suspend fun actualizarUsuario(usuarioId: Long, nombre: String, apellidos: String)

    @Query("UPDATE empresas SET nombre = :nombre, sector = :sector WHERE id = :empresaId")
    suspend fun actualizarEmpresa(empresaId: Long, nombre: String, sector: String)
}
