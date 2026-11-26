package com.example.gestorempresa.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.domain.auth.*
import com.example.gestorempresa.domain.profile.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProfileUiState(
    val cargando: Boolean = true,
    val perfil: PerfilAdministrador? = null,
    val editando: Boolean = false,
    val formulario: DatosPerfil = DatosPerfil(),
    val errores: Map<CampoPerfil, String> = emptyMap(),
    val guardando: Boolean = false,
    val mensaje: String? = null,
    val intentoValidacion: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {
    private val _ui = MutableStateFlow(ProfileUiState())
    val ui = _ui.asStateFlow()
    private var usuarioActual: Long? = null
    private var operacion: Job? = null
    private var generacion = 0

    init {
        viewModelScope.launch {
            authRepository.sesion.map { it?.usuario?.id }.distinctUntilChanged()
                .flatMapLatest { id ->
                    generacion++
                    operacion?.cancel()
                    usuarioActual = id
                    _ui.value = ProfileUiState(cargando = id != null)
                    if (id == null) flowOf(null)
                    else profileRepository.observarPerfil(id).catch {
                        _ui.update { it.copy(mensaje = "No se ha podido cargar el perfil.") }
                        emit(null)
                    }
                }.collect { perfil ->
                    _ui.update { it.copy(cargando = false, perfil = perfil?.takeIf { p -> p.usuarioId == usuarioActual }) }
                }
        }
    }

    fun empezarEdicion() {
        val state = _ui.value
        val perfil = state.perfil ?: return
        if (state.guardando) return
        _ui.update { it.copy(editando = true, formulario = perfil.toDatosPerfil(), errores = emptyMap(), mensaje = null) }
    }

    fun editar(datos: DatosPerfil) {
        if (_ui.value.guardando || !_ui.value.editando) return
        _ui.update { it.copy(formulario = datos,
            errores = if (it.errores.isEmpty()) emptyMap() else PerfilValidator.validar(datos)) }
    }

    fun cancelarEdicion() {
        if (_ui.value.guardando) return
        _ui.update { it.copy(editando = false, formulario = DatosPerfil(), errores = emptyMap()) }
    }

    fun guardar() {
        val state = _ui.value
        val perfil = state.perfil ?: return
        if (!state.editando || state.guardando) return
        val errors = PerfilValidator.validar(state.formulario)
        if (errors.isNotEmpty()) {
            _ui.update { it.copy(errores = errors, intentoValidacion = it.intentoValidacion + 1) }
            return
        }
        ejecutar("Perfil actualizado.", perfil.usuarioId) {
            profileRepository.guardarPerfil(perfil.usuarioId, perfil.empresa.id, PerfilValidator.normalizar(state.formulario))
            if (usuarioActual == perfil.usuarioId) _ui.update { it.copy(editando = false, formulario = DatosPerfil(), errores = emptyMap()) }
        }
    }

    fun cambiarFoto(uriImagen: String, usuarioEsperado: Long? = _ui.value.perfil?.usuarioId, alTerminar: () -> Unit = {}) {
        val perfil = _ui.value.perfil
        if (perfil == null || perfil.usuarioId != usuarioEsperado || _ui.value.guardando) { alTerminar(); return }
        ejecutar("Foto actualizada.", perfil.usuarioId, alTerminar) {
            profileRepository.cambiarFoto(perfil.usuarioId, uriImagen)
        }
    }

    fun eliminarFoto() {
        val perfil = _ui.value.perfil ?: return
        ejecutar("Foto eliminada.", perfil.usuarioId) { profileRepository.eliminarFoto(perfil.usuarioId) }
    }

    fun mensajeMostrado() { _ui.update { it.copy(mensaje = null) } }
    fun mostrarErrorFoto() { _ui.update { it.copy(mensaje = "No se ha podido abrir o ajustar la imagen. Prueba con otra fotografía.") } }

    private fun ejecutar(mensajeCorrecto: String, id: Long, alTerminar: () -> Unit = {}, accion: suspend () -> Unit) {
        if (_ui.value.guardando || id != usuarioActual) { alTerminar(); return }
        val version = generacion
        _ui.update { it.copy(guardando = true, mensaje = null) }
        operacion = viewModelScope.launch {
            try {
                accion()
                if (generacion == version) _ui.update { it.copy(mensaje = mensajeCorrecto) }
            } catch (e: CancellationException) { throw e }
            catch (e: ErrorServicioAuth) {
                if (generacion == version) _ui.update { it.copy(mensaje =
                    if (e.tipo == ErrorAuth.SIN_CONEXION) "No se pudo conectar. Tus cambios siguen en el formulario; vuelve a guardarlos con conexión."
                    else "No se han podido guardar los cambios. Inténtalo de nuevo.") }
            } catch (_: Exception) {
                if (generacion == version) _ui.update { it.copy(mensaje = "No se han podido guardar los cambios. Inténtalo de nuevo.") }
            } finally {
                alTerminar()
                if (generacion == version) _ui.update { it.copy(guardando = false) }
            }
        }
    }
}
