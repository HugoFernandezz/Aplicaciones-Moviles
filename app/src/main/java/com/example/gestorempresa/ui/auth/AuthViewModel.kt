package com.example.gestorempresa.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.domain.auth.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "", val password: String = "",
    val registro: RegistroUsuario = RegistroUsuario("", "", "", "", "", "", ""),
    val errores: Map<CampoAuth, String> = emptyMap(),
    val intentoValidacion: Int = 0,
    val mensaje: String? = null, val ocupado: Boolean = false,
    val emailRecuperacion: String = "", val recuperacionSolicitada: Boolean = false,
    val completandoPerfil: Boolean = false, val datosCuenta: DatosCuenta = DatosCuenta()
)

sealed interface AuthEvent { data class CuentaCreada(val email: String) : AuthEvent }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState(
        email = savedState["loginEmail"] ?: "",
        emailRecuperacion = savedState["emailRecuperacion"] ?: "",
        registro = RegistroUsuario(
            savedState["nombre"] ?: "", savedState["apellidos"] ?: "",
            savedState["registroEmail"] ?: "", "", "",
            savedState["empresa"] ?: "", savedState["sector"] ?: ""
        )
    ))
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()
    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)
    val eventos = eventChannel.receiveAsFlow()
    private var operation: Job? = null
    private val reintentos = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sesion: StateFlow<EstadoSesion> = reintentos.flatMapLatest {
        repository.sesion.map<SesionUsuario?, EstadoSesion> { session ->
            if (session == null) EstadoSesion.SinSesion else EstadoSesion.Autenticada(session)
        }.onStart { emit(EstadoSesion.Cargando) }.catch { emit(EstadoSesion.Fallo) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.Cargando)

    fun editarLogin(email: String = _ui.value.email, password: String = _ui.value.password) {
        if (_ui.value.ocupado) return
        savedState["loginEmail"] = email
        _ui.update { state -> state.copy(email = email, password = password,
            errores = if (state.errores.isEmpty()) emptyMap() else AuthValidator.validarLogin(email, password, 4096), mensaje = null) }
    }

    fun editarRegistro(datos: RegistroUsuario) {
        if (_ui.value.ocupado) return
        savedState["nombre"] = datos.nombre
        savedState["apellidos"] = datos.apellidos
        savedState["registroEmail"] = datos.email
        savedState["empresa"] = datos.empresa
        savedState["sector"] = datos.sector
        _ui.update { state -> state.copy(registro = datos,
            errores = if (state.errores.isEmpty()) emptyMap() else AuthValidator.validarRegistro(datos), mensaje = null) }
    }

    fun iniciarSesion() {
        val form = _ui.value
        val errors = AuthValidator.validarLogin(form.email, form.password, maxLength = 4096)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errores = errors) }; return }
        lanzar {
            val result = repository.iniciarSesion(form.email, form.password)
            _ui.update { it.copy(password = "") }
            mostrarResultado(result)
        }
    }

    fun editarDatosCuenta(datos: DatosCuenta) {
        if (_ui.value.ocupado) return
        _ui.update { it.copy(datosCuenta = datos, mensaje = null,
            errores = if (it.errores.isEmpty()) emptyMap() else AuthValidator.validarDatosCuenta(datos)) }
    }

    fun completarPerfil() {
        val datos = _ui.value.datosCuenta
        val errors = AuthValidator.validarDatosCuenta(datos)
        if (errors.isNotEmpty()) {
            _ui.update { it.copy(errores = errors, intentoValidacion = it.intentoValidacion + 1) }
            return
        }
        lanzar {
            when (val result = repository.completarPerfil(datos)) {
                ResultadoAuth.Correcto -> limpiarSecretos()
                is ResultadoAuth.Error -> {
                    if (result.tipo == ErrorAuth.CREDENCIALES_INVALIDAS) {
                        limpiarSecretos()
                        _ui.update { it.copy(mensaje = "Vuelve a iniciar sesión para completar tus datos.") }
                    } else mostrarResultado(result)
                }
            }
        }
    }

    fun cancelarCompletarPerfil() = cerrarSesion()

    fun registrar() {
        if (_ui.value.ocupado) return
        val form = _ui.value.registro
        val errors = AuthValidator.validarRegistro(form)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errores = errors, intentoValidacion = it.intentoValidacion + 1) }; return }
        lanzar {
            when (val result = repository.registrar(form)) {
                ResultadoAuth.Correcto -> {
                    val email = AuthValidator.normalizarEmail(form.email)
                    savedState["loginEmail"] = email
                    _ui.update { it.copy(email = email, registro = it.registro.copy(password = "", confirmarPassword = "")) }
                    eventChannel.send(AuthEvent.CuentaCreada(email))
                }
                is ResultadoAuth.Error -> {
                    mostrarResultado(result)
                    if (result.tipo == ErrorAuth.EMAIL_EN_USO) _ui.update { it.copy(
                        errores = mapOf(CampoAuth.EMAIL to "Ya existe una cuenta con este correo. Inicia sesión."),
                        intentoValidacion = it.intentoValidacion + 1) }
                }
            }
        }
    }

    fun cerrarSesion() = lanzar {
        val result = repository.cerrarSesion()
        if (result == ResultadoAuth.Correcto) limpiarSecretos()
        mostrarResultado(result)
    }

    fun abrirRecuperacion() {
        limpiarSecretos()
        val email = _ui.value.email
        savedState["emailRecuperacion"] = email
        _ui.update { it.copy(emailRecuperacion = email, recuperacionSolicitada = false) }
    }

    fun editarEmailRecuperacion(email: String) {
        if (_ui.value.ocupado) return
        savedState["emailRecuperacion"] = email
        _ui.update { it.copy(emailRecuperacion = email, recuperacionSolicitada = false, errores = emptyMap(), mensaje = null) }
    }

    fun recuperarPassword() {
        if (_ui.value.ocupado || _ui.value.recuperacionSolicitada) return
        val email = _ui.value.emailRecuperacion
        val errors = AuthValidator.validarEmail(email)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errores = errors) }; return }
        lanzar {
            when (val result = repository.recuperarPassword(AuthValidator.normalizarEmail(email))) {
                ResultadoAuth.Correcto -> _ui.update { it.copy(recuperacionSolicitada = true, errores = emptyMap(), mensaje = null) }
                is ResultadoAuth.Error -> mostrarResultado(result)
            }
        }
    }

    fun volverDeRecuperacion() {
        val email = AuthValidator.normalizarEmail(_ui.value.emailRecuperacion)
        savedState["loginEmail"] = email
        limpiarSecretos()
        _ui.update { it.copy(email = email) }
    }

    fun limpiarSecretos() {
        _ui.update { it.copy(password = "", registro = it.registro.copy(password = "", confirmarPassword = ""), errores = emptyMap(), mensaje = null, recuperacionSolicitada = false,
            completandoPerfil = false, datosCuenta = DatosCuenta()) }
    }
    fun reintentarSesion() { reintentos.update { it + 1 } }

    private fun lanzar(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        _ui.update { it.copy(ocupado = true, mensaje = null) }
        operation = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.update { it.copy(mensaje = "No se pudo completar la operación. Inténtalo de nuevo.") } }
            finally { _ui.update { it.copy(ocupado = false) } }
        }
    }

    private fun mostrarResultado(result: ResultadoAuth) {
        if (result == ResultadoAuth.Error(ErrorAuth.COMPLETAR_PERFIL)) {
            _ui.update { it.copy(completandoPerfil = true, datosCuenta = DatosCuenta(), errores = emptyMap(), mensaje = null) }
            return
        }
        if (result is ResultadoAuth.Error) _ui.update { state -> state.copy(mensaje = when (result.tipo) {
            ErrorAuth.EMAIL_EN_USO -> "Ya existe una cuenta con ese correo. Inicia sesión."
            ErrorAuth.CREDENCIALES_INVALIDAS -> "El correo o la contraseña no son correctos."
            ErrorAuth.BLOQUEO_TEMPORAL -> "Demasiados intentos. Espera 30 segundos antes de volver a probar."
            ErrorAuth.DATOS_INVALIDOS -> "Revisa los datos del formulario."
            ErrorAuth.ALMACENAMIENTO -> "No se han podido guardar o leer los datos. Inténtalo de nuevo."
            ErrorAuth.SERVICIO_NO_CONFIGURADO -> "El servicio de acceso por correo aún no está disponible. Contacta con el responsable de la aplicación."
            ErrorAuth.SIN_CONEXION -> "No se ha podido conectar. Comprueba tu conexión a Internet y vuelve a intentarlo."
            ErrorAuth.SERVICIO_NO_DISPONIBLE -> "El servicio de acceso no está disponible en este momento. Inténtalo más tarde."
            ErrorAuth.PERFIL_NO_DISPONIBLE -> "No se pueden leer los datos de esta cuenta. Contacta con el responsable para recuperarlos."
            ErrorAuth.COMPLETAR_PERFIL -> "Completa los datos de tu cuenta para continuar."
            ErrorAuth.PERFIL_SINCRONIZACION_PENDIENTE -> "La cuenta se creó, pero falta sincronizar el perfil. No vuelvas a registrarte: inicia sesión en este teléfono con conexión para completar el proceso."
            ErrorAuth.VINCULACION_NECESARIA -> "El correo ya tiene otra contraseña en el servicio de acceso. Contacta con el responsable para vincular tu cuenta anterior."
            ErrorAuth.DEMASIADAS_SOLICITUDES -> "Demasiadas solicitudes. Espera unos minutos antes de volver a intentarlo."
        }) }
    }
}
