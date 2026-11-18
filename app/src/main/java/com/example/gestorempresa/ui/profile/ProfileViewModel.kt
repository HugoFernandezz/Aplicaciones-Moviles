package com.example.gestorempresa.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.domain.auth.AuthRepository
import com.example.gestorempresa.domain.profile.CampoPerfil
import com.example.gestorempresa.domain.profile.DatosPerfil
import com.example.gestorempresa.domain.profile.PerfilAdministrador
import com.example.gestorempresa.domain.profile.PerfilValidator
import com.example.gestorempresa.domain.profile.ProfileRepository
import com.example.gestorempresa.domain.profile.toDatosPerfil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Todo lo que necesita la pantalla de Perfil para dibujarse. */
data class ProfileUiState(
    val cargando: Boolean = true,
    /** Datos guardados del administrador (null si no hay sesión). */
    val perfil: PerfilAdministrador? = null,
    /** true mientras se muestra el formulario de edición. */
    val editando: Boolean = false,
    /** Lo que el usuario está escribiendo en el formulario (aún sin guardar). */
    val formulario: DatosPerfil = DatosPerfil(),
    val errores: Map<CampoPerfil, String> = emptyMap(),
    val guardando: Boolean = false,
    /** Mensaje breve para mostrar al usuario (por ejemplo, "Perfil actualizado"). */
    val mensaje: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class) // necesario para usar flatMapLatest
@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        // Cada vez que cambia el usuario de la sesión, se observa su perfil en la base de datos.
        viewModelScope.launch {
            authRepository.sesion
                .map { it?.usuario?.id }
                .distinctUntilChanged()
                .flatMapLatest { usuarioId ->
                    if (usuarioId == null) flowOf(null) else profileRepository.observarPerfil(usuarioId)
                }
                .catch { _ui.update { it.copy(mensaje = "No se ha podido cargar el perfil.") }; emit(null) }
                .collect { perfil -> _ui.update { it.copy(cargando = false, perfil = perfil) } }
        }
    }

    // --- Edición de datos ---

    /** Abre el formulario relleno con los datos actuales. */
    fun empezarEdicion() {
        val perfil = _ui.value.perfil ?: return
        _ui.update { it.copy(editando = true, formulario = perfil.toDatosPerfil(), errores = emptyMap()) }
    }

    /** Se llama cada vez que el usuario escribe en un campo. */
    fun editar(datos: DatosPerfil) {
        _ui.update { it.copy(formulario = datos, errores = emptyMap()) }
    }

    fun cancelarEdicion() {
        _ui.update { it.copy(editando = false, errores = emptyMap()) }
    }

    fun guardar() {
        val estado = _ui.value
        val perfil = estado.perfil ?: return
        val errores = PerfilValidator.validar(estado.formulario)
        if (errores.isNotEmpty()) {
            _ui.update { it.copy(errores = errores) }
            return
        }
        ejecutar(mensajeCorrecto = "Perfil actualizado.") {
            profileRepository.guardarPerfil(perfil.usuarioId, perfil.empresa.id, estado.formulario)
            _ui.update { it.copy(editando = false) }
        }
    }

    // --- Foto ---

    /** [uriImagen] es la imagen elegida en la galería (content://...). */
    fun cambiarFoto(uriImagen: String) {
        val perfil = _ui.value.perfil ?: return
        ejecutar(mensajeCorrecto = "Foto actualizada.") { profileRepository.cambiarFoto(perfil.usuarioId, uriImagen) }
    }

    fun eliminarFoto() {
        val perfil = _ui.value.perfil ?: return
        ejecutar(mensajeCorrecto = "Foto eliminada.") { profileRepository.eliminarFoto(perfil.usuarioId) }
    }

    /** La pantalla llama a esto después de enseñar el mensaje, para no repetirlo. */
    fun mensajeMostrado() {
        _ui.update { it.copy(mensaje = null) }
    }

    /**
     * Ejecuta una operación de guardado en segundo plano:
     * marca "guardando", ejecuta la acción y muestra un mensaje de éxito o de error.
     */
    private fun ejecutar(mensajeCorrecto: String, accion: suspend () -> Unit) {
        if (_ui.value.guardando) return
        _ui.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val mensaje = try {
                accion()
                mensajeCorrecto
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "No se han podido guardar los cambios. Inténtalo de nuevo."
            }
            _ui.update { it.copy(guardando = false, mensaje = mensaje) }
        }
    }
}
