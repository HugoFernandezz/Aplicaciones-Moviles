package com.example.gestorempresa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.domain.auth.*
import com.example.gestorempresa.domain.settings.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val cargando: Boolean = true,
    val ajustes: AjustesApp = AjustesApp(),
    val email: String = "",
    val empresa: String = "",
    val sesionExpiraEn: Long? = null,
    val enviandoCorreo: Boolean = false,
    val mensaje: String? = null,
    val usuarioId: Long? = null,
    val guardando: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui = _ui.asStateFlow()
    private var usuarioId: Long? = null
    private var generacion = 0
    private var pendientes = 0
    private val mutex = Mutex()
    init {
        viewModelScope.launch {
            authRepository.sesion.flatMapLatest { sesion ->
                val id = sesion?.usuario?.id
                if (usuarioId != id) {
                    generacion++; pendientes = 0; usuarioId = id
                    _ui.value = SettingsUiState(cargando = id != null, usuarioId = id)
                }
                if (sesion == null) flowOf(null to AjustesApp())
                else settingsRepository.observarAjustes(sesion.usuario.id).map { sesion to it }
                    .catch {
                        _ui.update { it.copy(mensaje = "No se han podido cargar los ajustes.", cargando = false) }
                        emit(sesion to AjustesApp())
                    }
            }.collect { (sesion, preferencias) ->
                _ui.update { it.copy(cargando = false, ajustes = preferencias,
                    usuarioId = sesion?.usuario?.id, email = sesion?.usuario?.email.orEmpty(),
                    empresa = sesion?.empresa?.nombre.orEmpty(), sesionExpiraEn = sesion?.expiraEn) }
            }
        }
    }

    fun cambiarAvisosVencimientos(activado: Boolean) = guardar { it.copy(avisosVencimientos = activado) }
    fun cambiarDiasAntelacion(dias: Int) {
        if (dias in AjustesApp.opcionesAntelacion) guardar { it.copy(diasAntelacion = dias) }
    }
    fun cambiarAvisosTareas(activado: Boolean) = guardar { it.copy(avisosTareas = activado) }
    fun cambiarTema(tema: TemaApp) = guardar { it.copy(tema = tema) }

    fun solicitarCambioPassword() {
        val email = _ui.value.email
        if (usuarioId == null || email.isBlank() || _ui.value.enviandoCorreo) return
        val version = generacion
        _ui.update { it.copy(enviandoCorreo = true, mensaje = null) }
        viewModelScope.launch {
            try {
                val mensaje = when (val result = authRepository.recuperarPassword(email)) {
                    ResultadoAuth.Correcto -> "Solicitud aceptada. Revisa el correo de $email y la carpeta de spam para cambiar la contraseña."
                    is ResultadoAuth.Error -> when (result.tipo) {
                        ErrorAuth.SIN_CONEXION -> "No hay conexión a Internet. Inténtalo de nuevo."
                        ErrorAuth.DEMASIADAS_SOLICITUDES -> "Demasiadas solicitudes. Espera unos minutos."
                        else -> "No se ha podido solicitar el correo. Inténtalo más tarde."
                    }
                }
                if (version == generacion) _ui.update { it.copy(mensaje = mensaje) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (version == generacion) _ui.update { it.copy(mensaje = "No se ha podido solicitar el correo.") } }
            finally { if (version == generacion) _ui.update { it.copy(enviandoCorreo = false) } }
        }
    }

    fun mensajeMostrado() { _ui.update { it.copy(mensaje = null) } }

    private fun guardar(cambio: (AjustesApp) -> AjustesApp) {
        val id = usuarioId ?: return
        if (_ui.value.cargando) return
        val version = generacion
        pendientes++
        _ui.update { it.copy(guardando = true, mensaje = null) }
        viewModelScope.launch {
            try {
                mutex.withLock {
                    if (version != generacion || usuarioId != id) return@withLock
                    val anteriores = settingsRepository.observarAjustes(id).first()
                    val nuevos = cambio(anteriores)
                    settingsRepository.guardarAjustes(id, nuevos)
                    if (version == generacion) _ui.update { it.copy(ajustes = nuevos) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (version == generacion) _ui.update { it.copy(mensaje = "No se ha podido guardar el ajuste. Se mantiene el valor anterior.") }
            } finally {
                if (version == generacion) {
                    pendientes--
                    _ui.update { it.copy(guardando = pendientes > 0) }
                }
            }
        }
    }
}
