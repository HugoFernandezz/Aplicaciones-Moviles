package com.example.gestorempresa.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.gestorempresa.domain.auth.SesionUsuario
import com.example.gestorempresa.ui.home.HomeListsContent
import com.example.gestorempresa.ui.profile.ProfileScreen
import com.example.gestorempresa.ui.profile.ProfileViewModel
import com.example.gestorempresa.ui.settings.SettingsScreen
import com.example.gestorempresa.ui.settings.SettingsViewModel

/**
 * Las tres secciones de la barra de navegación inferior.
 * Cada una tiene un icono relleno (pestaña seleccionada) y otro de contorno (no seleccionada).
 */
enum class SeccionPrincipal(val titulo: String, val iconoSeleccionado: ImageVector, val icono: ImageVector) {
    INICIO("Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    PERFIL("Perfil", Icons.Filled.Person, Icons.Outlined.Person),
    AJUSTES("Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
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
                    val seleccionada = seccion == opcion
                    NavigationBarItem(
                        selected = seleccionada,
                        onClick = { seccion = opcion },
                        // contentDescription = null porque el texto de debajo ya describe el icono.
                        icon = { Icon(if (seleccionada) opcion.iconoSeleccionado else opcion.icono, contentDescription = null) },
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
