package com.example.gestorempresa.domain.profile

import com.example.gestorempresa.domain.auth.Empresa

/**
 * Información completa del administrador que se muestra en la pantalla de Perfil.
 *
 * Combina tres fuentes:
 *  - Datos de la cuenta (tabla `usuarios`, creada en el Registro).
 *  - Datos de la empresa (tabla `empresas`).
 *  - Datos personales extra que solo gestiona Perfil (tabla `perfiles`).
 */
data class PerfilAdministrador(
    val usuarioId: Long,
    val nombre: String,
    val apellidos: String,
    val email: String,
    val rol: String,
    val telefono: String,
    val dni: String,
    val cargo: String,
    /** Ruta del archivo de la foto en el almacenamiento interno. Null = avatar por defecto. */
    val fotoRuta: String?,
    val empresa: Empresa
) {
    val nombreCompleto: String get() = "$nombre $apellidos".trim()

    /** Iniciales para el avatar por defecto: "Juan Pérez" -> "JP". */
    val iniciales: String
        get() = listOf(nombre, apellidos)
            .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
            .joinToString("")
}

/** Campos que el administrador puede modificar desde el formulario de edición del perfil. */
data class DatosPerfil(
    val nombre: String = "",
    val apellidos: String = "",
    val telefono: String = "",
    val dni: String = "",
    val cargo: String = "",
    val empresaNombre: String = "",
    val sector: String = ""
)

/** Identifica cada campo del formulario para poder asociarle un mensaje de error. */
enum class CampoPerfil { NOMBRE, APELLIDOS, TELEFONO, DNI, CARGO, EMPRESA, SECTOR }

/** Convierte el perfil guardado en los datos iniciales del formulario de edición. */
fun PerfilAdministrador.toDatosPerfil() = DatosPerfil(
    nombre = nombre,
    apellidos = apellidos,
    telefono = telefono,
    dni = dni,
    cargo = cargo,
    empresaNombre = empresa.nombre,
    sector = empresa.sector
)
