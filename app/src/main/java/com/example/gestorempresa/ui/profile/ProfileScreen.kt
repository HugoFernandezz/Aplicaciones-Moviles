package com.example.gestorempresa.ui.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.domain.auth.Empresa
import com.example.gestorempresa.domain.profile.DatosPerfil
import com.example.gestorempresa.domain.profile.PerfilAdministrador
import com.example.gestorempresa.ui.profile.components.ProfileAvatar
import com.example.gestorempresa.ui.profile.components.ProfileEditForm
import com.example.gestorempresa.ui.profile.components.ProfileInfoRow
import com.example.gestorempresa.ui.profile.components.ProfileInfoSection

/**
 * Pantalla de Perfil conectada a su ViewModel.
 * Se encarga de lo que depende de Android (selector de imágenes y botón Atrás)
 * y delega el dibujo en [ProfileContent].
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    // Selector de fotos del sistema: no necesita pedir permisos de almacenamiento.
    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.cambiarFoto(uri.toString())
    }

    // Si se está editando, el botón Atrás cancela la edición en lugar de salir.
    BackHandler(enabled = state.editando) { viewModel.cancelarEdicion() }

    ProfileContent(
        state = state,
        onElegirFoto = { selectorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onQuitarFoto = viewModel::eliminarFoto,
        onEditar = viewModel::empezarEdicion,
        onCambio = viewModel::editar,
        onGuardar = viewModel::guardar,
        onCancelar = viewModel::cancelarEdicion,
        onMensajeMostrado = viewModel::mensajeMostrado,
        modifier = modifier
    )
}

/** Dibuja la pantalla a partir del estado. No conoce el ViewModel, por eso se puede previsualizar. */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onElegirFoto: () -> Unit,
    onQuitarFoto: () -> Unit,
    onEditar: () -> Unit,
    onCambio: (DatosPerfil) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0) // los márgenes del sistema los pone la navegación principal
    ) { padding ->
        val perfil = state.perfil
        when {
            state.cargando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            perfil == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No se ha encontrado el perfil del usuario.")
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Mi perfil", style = MaterialTheme.typography.headlineMedium)
                CabeceraPerfil(perfil, state.guardando, onElegirFoto, onQuitarFoto)

                if (state.editando) {
                    ProfileEditForm(
                        datos = state.formulario, errores = state.errores, email = perfil.email,
                        guardando = state.guardando, onCambio = onCambio, onGuardar = onGuardar, onCancelar = onCancelar
                    )
                } else {
                    DatosPerfilSecciones(perfil)
                    Button(onClick = onEditar, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil") }
                }
            }
        }
    }
}

/** Foto, nombre y cargo del administrador, con los botones para cambiar la foto. */
@Composable
private fun CabeceraPerfil(perfil: PerfilAdministrador, guardando: Boolean, onElegirFoto: () -> Unit, onQuitarFoto: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProfileAvatar(perfil.fotoRuta, perfil.iniciales, onClick = if (guardando) null else onElegirFoto)
        Text(perfil.nombreCompleto, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            listOf(perfil.cargo, perfil.empresa.nombre).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onElegirFoto, enabled = !guardando) { Text(if (perfil.fotoRuta == null) "Añadir foto" else "Cambiar foto") }
            if (perfil.fotoRuta != null) TextButton(onClick = onQuitarFoto, enabled = !guardando) { Text("Quitar foto") }
        }
    }
}

/** Las tres tarjetas de información: datos personales, empresa y cuenta. */
@Composable
private fun DatosPerfilSecciones(perfil: PerfilAdministrador) {
    ProfileInfoSection("Datos personales") {
        ProfileInfoRow("Nombre", perfil.nombre)
        ProfileInfoRow("Apellidos", perfil.apellidos)
        ProfileInfoRow("Teléfono", perfil.telefono)
        ProfileInfoRow("DNI / NIE", perfil.dni)
        ProfileInfoRow("Cargo", perfil.cargo, mostrarSeparador = false)
    }
    ProfileInfoSection("Empresa") {
        ProfileInfoRow("Nombre", perfil.empresa.nombre)
        ProfileInfoRow("Sector", perfil.empresa.sector, mostrarSeparador = false)
    }
    ProfileInfoSection("Cuenta") {
        ProfileInfoRow("Correo electrónico", perfil.email)
        ProfileInfoRow("Rol", if (perfil.rol == "ADMIN") "Administrador" else perfil.rol, mostrarSeparador = false)
    }
}

// --- Vista previa en Android Studio (pestaña "Split" o "Design") ---

private val perfilEjemplo = PerfilAdministrador(
    usuarioId = 1, nombre = "Juan", apellidos = "Pérez López", email = "juan@construccionesmurcia.es",
    rol = "ADMIN", telefono = "600 123 456", dni = "", cargo = "Gerente", fotoRuta = null,
    empresa = Empresa(1, "Construcciones Murcia", "Construcción")
)

@Preview(name = "Perfil", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfilePreview() {
    MaterialTheme {
        ProfileContent(ProfileUiState(cargando = false, perfil = perfilEjemplo), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Perfil · editando", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileEditPreview() {
    MaterialTheme {
        ProfileContent(
            ProfileUiState(cargando = false, perfil = perfilEjemplo, editando = true, formulario = DatosPerfil("Juan", "Pérez López", "600 123 456", "", "Gerente", "Construcciones Murcia", "Construcción")),
            {}, {}, {}, {}, {}, {}, {}
        )
    }
}
