package com.example.gestorempresa.domain.auth

/** Perfil compartido sin credenciales. Los IDs Long de Room solo identifican filas locales. */
data class PerfilCuentaRemoto(
    val uid: String,
    val nombre: String,
    val apellidos: String,
    val empresaId: String,
    val empresaNombre: String,
    val sector: String,
    val creadaEn: Long,
    val telefono: String = "",
    val dni: String = "",
    val cargo: String = "",
    val direccion: String = "",
    val telefonoRegion: String? = null
)

interface PerfilCuentaService {
    suspend fun obtener(uid: String): PerfilCuentaRemoto?
    /** Transacción: conserva el perfil existente si otro dispositivo ya lo publicó. */
    suspend fun crearSiFalta(perfil: PerfilCuentaRemoto): PerfilCuentaRemoto
    suspend fun actualizar(perfil: PerfilCuentaRemoto)
}
