package com.example.gestorempresa.domain.settings

/** Apariencia visual de la aplicación. */
enum class TemaApp(val titulo: String) {
    SISTEMA("Según el sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro")
}

/**
 * Preferencias del administrador. Se guardan por usuario, así cada cuenta
 * conserva su propia configuración en el mismo dispositivo.
 *
 * Los valores por defecto son los que se usan si el usuario nunca ha tocado Ajustes.
 */
data class AjustesApp(
    /** Avisar de reconocimientos médicos, cursos y documentos próximos a caducar. */
    val avisosVencimientos: Boolean = true,
    /** Con cuántos días de antelación se considera que una fecha está "próxima". */
    val diasAntelacion: Int = 15,
    /** Avisar de tareas y trabajos pendientes. */
    val avisosTareas: Boolean = true,
    /** Claro por defecto: el módulo de Inicio todavía solo tiene colores claros. */
    val tema: TemaApp = TemaApp.CLARO
) {
    companion object {
        /** Opciones que se ofrecen en Ajustes para los días de antelación. */
        val opcionesAntelacion = listOf(7, 15, 30, 60)
    }
}
