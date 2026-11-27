package com.example.gestorempresa.domain.profile

import com.example.gestorempresa.domain.auth.AuthValidator
import com.example.gestorempresa.domain.phone.InternationalPhones

/**
 * Reglas de validación del formulario de Perfil.
 * Devuelve un mapa vacío si todo es correcto, o un mensaje de error por cada campo incorrecto.
 */
object PerfilValidator {

    /** DNI (8 números + letra) o NIE (X/Y/Z + 7 números + letra). */
    private val dniPattern = Regex("^[XYZxyz]?[0-9]{7,8}[A-Za-z]$")

    fun validar(datos: DatosPerfil): Map<CampoPerfil, String> = buildMap {
        // Mismas longitudes que en el Registro para que los datos sean coherentes.
        if (datos.nombre.trim().length !in 2..60) put(CampoPerfil.NOMBRE, "El nombre debe tener entre 2 y 60 caracteres.")
        if (datos.apellidos.trim().length !in 2..100) put(CampoPerfil.APELLIDOS, "Los apellidos deben tener entre 2 y 100 caracteres.")
        if (datos.empresaNombre.trim().length !in 2..120) put(CampoPerfil.EMPRESA, "La empresa debe tener entre 2 y 120 caracteres.")
        if (datos.sector !in AuthValidator.sectores) put(CampoPerfil.SECTOR, "Selecciona un sector.")

        // Campos opcionales: solo se validan si se han rellenado.
        val telefono = datos.telefono.trim()
        if (InternationalPhones.countries.none { it.region == datos.telefonoRegion }) put(CampoPerfil.TELEFONO, "Selecciona un país válido.")
        if (telefono.isNotEmpty() && InternationalPhones.toE164(telefono, datos.telefonoRegion) == null) put(CampoPerfil.TELEFONO, "Introduce un teléfono válido para el país seleccionado.")
        val dni = datos.dni.trim()
        if (dni.isNotEmpty() && !dniPattern.matches(dni)) put(CampoPerfil.DNI, "Introduce un DNI o NIE válido.")
        if (datos.cargo.trim().length > 60) put(CampoPerfil.CARGO, "El cargo admite hasta 60 caracteres.")
        if (datos.direccion.trim().length > 240) put(CampoPerfil.DIRECCION, "La dirección admite hasta 240 caracteres.")
    }

    /** Limpia espacios sobrantes y pone el DNI en mayúsculas antes de guardar. */
    fun normalizar(datos: DatosPerfil) = datos.copy(
        nombre = datos.nombre.trim(),
        apellidos = datos.apellidos.trim(),
        telefono = if (datos.telefono.isBlank()) "" else InternationalPhones.toE164(datos.telefono, datos.telefonoRegion) ?: datos.telefono.trim(),
        dni = datos.dni.trim().uppercase(),
        cargo = datos.cargo.trim(),
        empresaNombre = datos.empresaNombre.trim(),
        direccion = datos.direccion.trim()
    )
}
