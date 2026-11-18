package com.example.gestorempresa.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.BuildConfig
import com.example.gestorempresa.domain.settings.AjustesApp
import com.example.gestorempresa.domain.settings.TemaApp
import com.example.gestorempresa.ui.settings.components.SettingsInfoRow
import com.example.gestorempresa.ui.settings.components.SettingsRadioRow
import com.example.gestorempresa.ui.settings.components.SettingsSection
import com.example.gestorempresa.ui.settings.components.SettingsSwitchRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pantalla de Ajustes conectada a su ViewModel.
 * [onAbrirPerfil] y [onCerrarSesion] los proporciona la navegación principal.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onAbrirPerfil: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onAvisosVencimientos = viewModel::cambiarAvisosVencimientos,
        onDiasAntelacion = viewModel::cambiarDiasAntelacion,
        onAvisosTareas = viewModel::cambiarAvisosTareas,
        onTema = viewModel::cambiarTema,
        onCambiarPassword = viewModel::solicitarCambioPassword,
        onAbrirPerfil = onAbrirPerfil,
        onCerrarSesion = onCerrarSesion,
        onMensajeMostrado = viewModel::mensajeMostrado,
        modifier = modifier
    )
}

/** Dibuja la pantalla a partir del estado. No conoce el ViewModel, por eso se puede previsualizar. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onAvisosVencimientos: (Boolean) -> Unit,
    onDiasAntelacion: (Int) -> Unit,
    onAvisosTareas: (Boolean) -> Unit,
    onTema: (TemaApp) -> Unit,
    onCambiarPassword: () -> Unit,
    onAbrirPerfil: () -> Unit,
    onCerrarSesion: () -> Unit,
    onMensajeMostrado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.mensaje) {
        state.mensaje?.let {
            snackbar.showSnackbar(it)
            onMensajeMostrado()
        }
    }

    // Diálogos de confirmación para las acciones importantes.
    var confirmarCierre by rememberSaveable { mutableStateOf(false) }
    var confirmarPassword by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0) // los márgenes del sistema los pone la navegación principal
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
            SeccionNotificaciones(state.ajustes, onAvisosVencimientos, onDiasAntelacion, onAvisosTareas)
            SeccionPreferencias(state.ajustes.tema, onTema)
            SeccionCuenta(state, onAbrirPerfil)
            SeccionSeguridad(
                state = state,
                onCambiarPassword = { confirmarPassword = true },
                onCerrarSesion = { confirmarCierre = true }
            )
            Text(
                "Gestor Empresa · versión ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmarPassword) {
        AlertDialog(
            onDismissRequest = { confirmarPassword = false },
            title = { Text("Cambiar contraseña") },
            text = { Text("Te enviaremos un correo a ${state.email} con un enlace para elegir una contraseña nueva.") },
            confirmButton = { Button(onClick = { confirmarPassword = false; onCambiarPassword() }) { Text("Enviar correo") } },
            dismissButton = { TextButton(onClick = { confirmarPassword = false }) { Text("Cancelar") } }
        )
    }
    if (confirmarCierre) {
        AlertDialog(
            onDismissRequest = { confirmarCierre = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tendrás que volver a introducir tu correo y contraseña para entrar.") },
            confirmButton = { Button(onClick = { confirmarCierre = false; onCerrarSesion() }) { Text("Cerrar sesión") } },
            dismissButton = { TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") } }
        )
    }
}

// --- Secciones de la pantalla ---

@Composable
private fun SeccionNotificaciones(
    ajustes: AjustesApp,
    onAvisosVencimientos: (Boolean) -> Unit,
    onDiasAntelacion: (Int) -> Unit,
    onAvisosTareas: (Boolean) -> Unit
) {
    SettingsSection("Notificaciones") {
        SettingsSwitchRow(
            titulo = "Avisos de vencimientos",
            descripcion = "Reconocimientos médicos, cursos y documentos próximos a caducar.",
            activado = ajustes.avisosVencimientos,
            onCambio = onAvisosVencimientos
        )
        // Los días de antelación solo tienen sentido si los avisos están activados.
        if (ajustes.avisosVencimientos) {
            Text("Avisar con antelación de:", style = MaterialTheme.typography.bodyMedium)
            // FlowRow pasa las opciones a otra línea si no caben en la pantalla.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AjustesApp.opcionesAntelacion.forEach { dias ->
                    FilterChip(
                        selected = ajustes.diasAntelacion == dias,
                        onClick = { onDiasAntelacion(dias) },
                        label = { Text("$dias días") }
                    )
                }
            }
        }
        HorizontalDivider()
        SettingsSwitchRow(
            titulo = "Avisos de tareas",
            descripcion = "Tareas y trabajos pendientes o con la fecha vencida.",
            activado = ajustes.avisosTareas,
            onCambio = onAvisosTareas
        )
    }
}

@Composable
private fun SeccionPreferencias(temaActual: TemaApp, onTema: (TemaApp) -> Unit) {
    SettingsSection("Preferencias") {
        Text("Tema de la aplicación", style = MaterialTheme.typography.bodyMedium)
        TemaApp.entries.forEach { tema ->
            SettingsRadioRow(titulo = tema.titulo, seleccionado = tema == temaActual, onElegir = { onTema(tema) })
        }
    }
}

@Composable
private fun SeccionCuenta(state: SettingsUiState, onAbrirPerfil: () -> Unit) {
    SettingsSection("Cuenta") {
        SettingsInfoRow("Correo electrónico", state.email)
        SettingsInfoRow("Empresa", state.empresa)
        OutlinedButton(onClick = onAbrirPerfil, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil") }
    }
}

@Composable
private fun SeccionSeguridad(state: SettingsUiState, onCambiarPassword: () -> Unit, onCerrarSesion: () -> Unit) {
    SettingsSection("Seguridad") {
        state.sesionExpiraEn?.let { SettingsInfoRow("Sesión válida hasta", formatearFecha(it)) }
        OutlinedButton(onClick = onCambiarPassword, enabled = !state.enviandoCorreo, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.enviandoCorreo) "Enviando correo…" else "Cambiar contraseña")
        }
        Button(
            onClick = onCerrarSesion,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) { Text("Cerrar sesión") }
    }
}

/** Convierte milisegundos en un texto como "18/11/2026 17:30". */
private fun formatearFecha(milisegundos: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(milisegundos))

// --- Vista previa en Android Studio (pestaña "Split" o "Design") ---

@Preview(name = "Ajustes", showBackground = true, widthDp = 393, heightDp = 1100)
@Composable
private fun SettingsPreview() {
    MaterialTheme {
        SettingsContent(
            state = SettingsUiState(
                cargando = false, email = "juan@construccionesmurcia.es",
                empresa = "Construcciones Murcia", sesionExpiraEn = 1_795_000_000_000
            ),
            onAvisosVencimientos = {}, onDiasAntelacion = {}, onAvisosTareas = {}, onTema = {},
            onCambiarPassword = {}, onAbrirPerfil = {}, onCerrarSesion = {}, onMensajeMostrado = {}
        )
    }
}
