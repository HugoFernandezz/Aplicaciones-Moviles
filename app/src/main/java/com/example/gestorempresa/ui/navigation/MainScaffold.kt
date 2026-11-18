package com.example.gestorempresa.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gestorempresa.domain.auth.SesionUsuario
import com.example.gestorempresa.ui.home.HomeListsContent
import com.example.gestorempresa.ui.profile.ProfileScreen
import com.example.gestorempresa.ui.profile.ProfileViewModel
import com.example.gestorempresa.ui.settings.SettingsScreen
import com.example.gestorempresa.ui.settings.SettingsViewModel

/** Las tres secciones de la barra de navegación inferior. */
enum class SeccionPrincipal(val titulo: String, val icono: String) {
    INICIO("Inicio", "🏠"),
    PERFIL("Perfil", "👤"),
    AJUSTES("Ajustes", "⚙️")
}

/**
 * Estructura principal de la app una vez iniciada la sesión:
 * el contenido de la sección elegida arriba y la barra de navegación abajo.
 */
@Composable
fun MainScaffold(
    sesion: SesionUsuario,
    onCerrarSesion: () -> Unit,
    profileViewModel: ProfileViewModel,
    settingsViewModel: SettingsViewModel
) {
    var seccion by rememberSaveable { mutableStateOf(SeccionPrincipal.INICIO) }

    // Guarda el estado de cada sección (por ejemplo, el listado abierto en Inicio)
    // para que no se pierda al cambiar de pestaña y volver.
    val estadoSecciones = rememberSaveableStateHolder()

    // Desde Perfil o Ajustes, el botón Atrás vuelve a Inicio en lugar de cerrar la app.
    BackHandler(enabled = seccion != SeccionPrincipal.INICIO) { seccion = SeccionPrincipal.INICIO }

    Scaffold(
        bottomBar = {
            NavigationBar {
                SeccionPrincipal.entries.forEach { opcion ->
                    NavigationBarItem(
                        selected = seccion == opcion,
                        onClick = { seccion = opcion },
                        icon = { Text(opcion.icono) },
                        label = { Text(opcion.titulo) }
                    )
                }
            }
        }
    ) { padding ->
        // imePadding: deja hueco para el teclado al escribir en los formularios.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            estadoSecciones.SaveableStateProvider(seccion.name) {
                when (seccion) {
                    SeccionPrincipal.INICIO -> HomeListsContent(session = sesion)
                    SeccionPrincipal.PERFIL -> ProfileScreen(profileViewModel)
                    SeccionPrincipal.AJUSTES -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onAbrirPerfil = { seccion = SeccionPrincipal.PERFIL },
                        onCerrarSesion = onCerrarSesion
                    )
                }
            }
        }
    }
}
