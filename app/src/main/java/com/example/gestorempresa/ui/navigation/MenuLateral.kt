package com.example.gestorempresa.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.profile.components.ProfileAvatar

/** Una opción del menú lateral. */
data class OpcionMenu(
    /** Identificador de la pantalla a la que lleva (lo interpreta quien usa el menú). */
    val ruta: String,
    val titulo: String,
    val icono: ImageVector,
    /** Número que aparece a la derecha (por ejemplo, avisos pendientes). 0 = no se muestra. */
    val contador: Int = 0,
    /** Si es true, el contador se pinta en rojo. */
    val urgente: Boolean = false,
    /** Dibuja una línea separadora encima de esta opción para agrupar el menú. */
    val separadorAntes: Boolean = false
)

/**
 * Contenido del menú lateral: una cabecera con la empresa y el usuario, y la lista de opciones.
 * Se usa dentro de un ModalNavigationDrawer.
 */
@Composable
fun MenuLateral(
    titulo: String,
    subtitulo: String,
    opciones: List<OpcionMenu>,
    rutaSeleccionada: String,
    onElegir: (String) -> Unit,
    fotoRuta: String? = null,
    iniciales: String = ""
) {
    ModalDrawerSheet(Modifier.testTag("menu-lateral")) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            // Cabecera
            Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
                ProfileAvatar(fotoRuta, iniciales.ifBlank { subtitulo.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("") },
                    tamano = 64.dp, modifier = Modifier.testTag("menu-profile-avatar"))
                Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(bottom = 8.dp))

            // Opciones
            opciones.forEach { opcion ->
                if (opcion.separadorAntes) HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text(opcion.titulo) },
                    icon = { Icon(opcion.icono, contentDescription = null) },
                    selected = opcion.ruta == rutaSeleccionada,
                    onClick = { onElegir(opcion.ruta) },
                    badge = {
                        if (opcion.contador > 0) Text(
                            text = opcion.contador.toString(),
                            color = if (opcion.urgente) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}

/** Barra superior con el botón ☰ que abre el menú lateral y el título de la sección actual. */
@Composable
fun BarraSuperior(titulo: String, onAbrirMenu: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onAbrirMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "Abrir menú")
        }
        Text(titulo, style = MaterialTheme.typography.titleLarge)
    }
}
