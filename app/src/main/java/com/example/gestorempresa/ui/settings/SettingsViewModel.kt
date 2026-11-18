package com.example.gestorempresa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.domain.auth.AuthRepository
import com.example.gestorempresa.domain.auth.ErrorAuth
import com.example.gestorempresa.domain.auth.ResultadoAuth
import com.example.gestorempresa.domain.settings.AjustesApp
import com.example.gestorempresa.domain.settings.SettingsRepository
import com.example.gestorempresa.domain.settings.TemaApp
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Todo lo que necesita la pantalla de Ajustes para dibujarse. */
data class SettingsUiState(
    val cargando: Boolean = true,
    val ajustes: AjustesApp = AjustesApp(),
    /** Datos de la cuenta que se muestran en la sección "Cuenta". */
    val email: String = "",
    val empresa: String = "",
    /** Momento (en milisegundos) en que caduca la sesión actual. */
    val sesionExpiraEn: Long? = null,
    val enviandoCorreo: Boolean = false,
    val mensaje: String? = null
)

/**
 * Gestiona las preferencias del usuario y la seguridad de la cuenta.
 * El cierre de sesión NO está aquí: lo hace AuthViewModel (Miembro 1), y la pantalla lo recibe como parámetro.
 */
@OptIn(ExperimentalCoroutinesApi::class) // necesario para usar flatMapLatest
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    /** Usuario de la sesión actual; hace falta para saber de quién son los ajustes que se guardan. */
    private var usuarioId: Long? = null

    init {
        viewModelScope.launch {
            authRepository.sesion
                .flatMapLatest { sesion ->
                    if (sesion == null) flowOf(null to AjustesApp())
                    else settingsRepository.observarAjustes(sesion.usuario.id).map { ajustes -> sesion to ajustes }
                }
                .catch { _ui.update { it.copy(mensaje = "No se han podido cargar los ajustes.") } }
                .collect { (sesion, ajustes) ->
                    usuarioId = sesion?.usuario?.id
                    _ui.update {
                        it.copy(
                            cargando = false,
                            ajustes = ajustes,
                            email = sesion?.usuario?.email.orEmpty(),
                            empresa = sesion?.empresa?.nombre.orEmpty(),
                            sesionExpiraEn = sesion?.expiraEn
                        )
                    }
                }
        }
    }

    // --- Notificaciones ---

    fun cambiarAvisosVencimientos(activado: Boolean) = guardar { it.copy(avisosVencimientos = activado) }

    fun cambiarDiasAntelacion(dias: Int) = guardar { it.copy(diasAntelacion = dias) }

    fun cambiarAvisosTareas(activado: Boolean) = guardar { it.copy(avisosTareas = activado) }

    // --- Preferencias ---

    fun cambiarTema(tema: TemaApp) = guardar { it.copy(tema = tema) }

    // --- Seguridad ---

    /** Envía al correo del usuario un enlace para cambiar la contraseña (usa el servicio del Miembro 1). */
    fun solicitarCambioPassword() {
        val email = _ui.value.email
        if (email.isBlank() || _ui.value.enviandoCorreo) return
        _ui.update { it.copy(enviandoCorreo = true) }
        viewModelScope.launch {
            val mensaje = try {
                when (val resultado = authRepository.recuperarPassword(email)) {
                    ResultadoAuth.Correcto -> "Te hemos enviado un correo a $email para cambiar la contraseña."
                    is ResultadoAuth.Error -> when (resultado.tipo) {
                        ErrorAuth.SIN_CONEXION -> "No hay conexión a Internet. Inténtalo de nuevo."
                        ErrorAuth.DEMASIADAS_SOLICITUDES -> "Demasiadas solicitudes. Espera unos minutos."
                        else -> "No se ha podido enviar el correo. Inténtalo más tarde."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "No se ha podido enviar el correo. Inténtalo más tarde."
            }
            _ui.update { it.copy(enviandoCorreo = false, mensaje = mensaje) }
        }
    }

    fun mensajeMostrado() {
        _ui.update { it.copy(mensaje = null) }
    }

    /**
     * Aplica un cambio a los ajustes: primero se ve en pantalla al momento
     * y después se guarda en la base de datos.
     */
    private fun guardar(cambio: (AjustesApp) -> AjustesApp) {
        val id = usuarioId ?: return
        val nuevos = cambio(_ui.value.ajustes)
        _ui.update { it.copy(ajustes = nuevos) }
        viewModelScope.launch {
            try {
                settingsRepository.guardarAjustes(id, nuevos)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(mensaje = "No se ha podido guardar el ajuste.") }
            }
        }
    }
}
