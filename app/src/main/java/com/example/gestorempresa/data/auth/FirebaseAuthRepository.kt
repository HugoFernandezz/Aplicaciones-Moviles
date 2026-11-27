package com.example.gestorempresa.data.auth

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.*
import com.example.gestorempresa.domain.auth.*
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val db: AppDatabase,
    private val local: RoomAuthRepository,
    private val remote: EmailAuthService,
    private val hasher: PasswordHasher,
    private val clock: Clock,
    private val perfiles: PerfilCuentaService
) : AuthRepository {
    private val dao = db.authDao()
    private val mutex = Mutex()
    private val autenticando = MutableStateFlow(false)
    private var identidadPendiente: IdentidadRemota? = null
    // Firebase renueva sus tokens; siete días solo limita las cuentas locales antiguas.
    override val sesion = combine(local.sesion, remote.identidad, autenticando, flow {
        emit(Unit) // La red no bloquea la presentación de una sesión local válida.
        while (true) {
            remote.actualizarSesion()
            publicarPerfilDeSesionAbierta()
            emit(Unit)
            delay(60_000)
        }
    }) { session, identity, busy, _ ->
        session?.takeIf { !busy && (it.usuario.firebaseUid == null || it.usuario.firebaseUid == identity?.uid) }
    }.distinctUntilChanged()

    override suspend fun registrar(datos: RegistroUsuario): ResultadoAuth = ejecutar {
        identidadPendiente = null
        if (AuthValidator.validarRegistro(datos).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        if (!remote.configurado) return@ejecutar ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        val email = AuthValidator.normalizarEmail(datos.email)
        if (dao.buscarPorEmail(email) != null) return@ejecutar ResultadoAuth.Error(ErrorAuth.EMAIL_EN_USO)
        autenticando.value = true
        try {
            val identity = remote.registrar(email, datos.password)
            // Conservamos una copia recuperable. Nunca borramos una cuenta por un timeout:
            // el servidor podría haber confirmado el alta aunque su respuesta no llegase.
            val profile = PerfilCuentaRemoto(identity.uid, datos.nombre.trim(), datos.apellidos.trim(),
                identity.uid, datos.empresa.trim(), datos.sector, clock.millis())
            val published = try {
                perfiles.crearSiFalta(profile)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                guardarCopia(identity, profile, abrirSesion = false)
                return@ejecutar ResultadoAuth.Error(ErrorAuth.PERFIL_SINCRONIZACION_PENDIENTE)
            }
            guardarCopia(identity, published, abrirSesion = false)
            ResultadoAuth.Correcto
        } finally { remote.salir(); autenticando.value = false }
    }

    override suspend fun iniciarSesion(email: String, password: String): ResultadoAuth = ejecutar {
        identidadPendiente = null
        if (AuthValidator.validarLogin(email, password, maxLength = 4096).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        val normalized = AuthValidator.normalizarEmail(email)
        val localProfile = dao.buscarPorEmail(normalized)
        if (!remote.configurado) {
            return@ejecutar if (localProfile != null && localProfile.firebaseUid == null) local.iniciarSesion(email, password)
            else ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        }
        autenticando.value = true
        var success = false
        try {
            val identity = if (localProfile != null && localProfile.firebaseUid == null) {
                // Un correo coincidente no acredita la propiedad de los datos antiguos.
                val now = clock.millis()
                if (localProfile.bloqueoHasta > now) return@ejecutar ResultadoAuth.Error(ErrorAuth.BLOQUEO_TEMPORAL)
                if (!hasher.comprobar(password, PasswordDigest(localProfile.passwordHash, localProfile.passwordSalt, localProfile.passwordIterations))) {
                    val previos = if (localProfile.bloqueoHasta in 1..now) 0 else localProfile.intentosFallidos
                    val failures = previos + 1
                    dao.actualizarIntentos(localProfile.id, failures, if (failures >= 5) now + 30_000 else 0)
                    return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
                }
                try { remote.ingresar(normalized, password) }
                catch (e: ErrorServicioAuth) {
                    if (e.tipo != ErrorAuth.CREDENCIALES_INVALIDAS) throw e
                    try { remote.registrar(normalized, password) }
                    catch (collision: ErrorServicioAuth) {
                        if (collision.tipo == ErrorAuth.EMAIL_EN_USO) throw ErrorServicioAuth(ErrorAuth.VINCULACION_NECESARIA)
                        throw collision
                    }
                }
            } else remote.ingresar(normalized, password)

            if (AuthValidator.normalizarEmail(identity.email) != normalized ||
                (localProfile?.firebaseUid != null && localProfile.firebaseUid != identity.uid)) {
                return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
            }
            val profile = perfiles.obtener(identity.uid) ?: run {
                // Se publica una vez desde el teléfono que conserva los datos y solo
                // después de verificar la identidad y, si procede, la clave antigua.
                val previous = localProfile ?: dao.buscarPorUid(identity.uid)
                if (previous == null) {
                    identidadPendiente = identity
                    return@ejecutar ResultadoAuth.Error(ErrorAuth.COMPLETAR_PERFIL)
                }
                perfiles.crearSiFalta(perfilLocal(identity, previous))
            }
            guardarCopia(identity, profile, abrirSesion = true)
            success = true
            ResultadoAuth.Correcto
        } finally {
            if (!success && identidadPendiente == null) remote.salir()
            autenticando.value = false
        }
    }

    override suspend fun completarPerfil(datos: DatosCuenta): ResultadoAuth = ejecutar {
        if (AuthValidator.validarDatosCuenta(datos).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        val pending = identidadPendiente ?: return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
        autenticando.value = true
        try {
            remote.actualizarSesion()
            val identity = remote.identidad.first()
            if (identity != pending) {
                identidadPendiente = null
                return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
            }
            // La transacción conserva los datos si el primer teléfono los publicó entretanto.
            // No se crea otra cuenta de Authentication ni se elige una empresa ajena.
            val profile = perfiles.crearSiFalta(PerfilCuentaRemoto(pending.uid, datos.nombre.trim(), datos.apellidos.trim(),
                pending.uid, datos.empresa.trim(), datos.sector, clock.millis()))
            guardarCopia(pending, profile, abrirSesion = true)
            identidadPendiente = null
            ResultadoAuth.Correcto
        } finally { autenticando.value = false }
    }

    private suspend fun perfilLocal(identity: IdentidadRemota, usuario: UsuarioEntity): PerfilCuentaRemoto {
        if (usuario.firebaseUid != null && usuario.firebaseUid != identity.uid) throw ErrorServicioAuth(ErrorAuth.VINCULACION_NECESARIA)
        val company = requireNotNull(dao.buscarEmpresa(usuario.empresaId))
        val extras = db.perfilDao().buscarPerfil(usuario.id)
        return PerfilCuentaRemoto(identity.uid, usuario.nombre, usuario.apellidos,
            company.remoteId ?: identity.uid, company.nombre, company.sector, company.creadaEn,
            extras?.telefono.orEmpty(), extras?.dni.orEmpty(), extras?.cargo.orEmpty(), extras?.direccion.orEmpty(), extras?.telefonoRegion)
    }

    /** También migra perfiles cuando la actualización conserva una sesión ya abierta. */
    private suspend fun publicarPerfilDeSesionAbierta() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val identity = remote.identidad.first() ?: return@withLock
                if (identidadPendiente == identity) {
                    perfiles.obtener(identity.uid)?.let {
                        guardarCopia(identity, it, abrirSesion = true)
                        identidadPendiente = null
                    }
                    return@withLock
                }
                val session = dao.observarSesion().first() ?: return@withLock
                if (session.firebaseUid != identity.uid) return@withLock
                if (perfiles.obtener(identity.uid) == null) {
                    val usuario = dao.buscarUsuario(session.usuarioId) ?: return@withLock
                    perfiles.crearSiFalta(perfilLocal(identity, usuario))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Conserva la sesión válida y reintenta al recuperar la conexión. */ }
        }
    }

    private suspend fun guardarCopia(identity: IdentidadRemota, profile: PerfilCuentaRemoto, abrirSesion: Boolean) {
        if (profile.uid != identity.uid || profile.empresaId != identity.uid) throw ErrorServicioAuth(ErrorAuth.PERFIL_NO_DISPONIBLE)
        db.withTransaction {
            val email = AuthValidator.normalizarEmail(identity.email)
            val old = dao.buscarPorUid(identity.uid) ?: dao.buscarPorEmail(email)
            if (old?.firebaseUid != null && old.firebaseUid != identity.uid) throw ErrorServicioAuth(ErrorAuth.VINCULACION_NECESARIA)
            val companyId = old?.empresaId ?: dao.insertarEmpresa(EmpresaEntity(
                nombre = profile.empresaNombre, sector = profile.sector, creadaEn = profile.creadaEn, remoteId = profile.empresaId))
            dao.actualizarEmpresa(EmpresaEntity(companyId, profile.empresaNombre, profile.sector, profile.creadaEn, profile.empresaId))
            val updated = UsuarioEntity(id = old?.id ?: 0, nombre = profile.nombre, apellidos = profile.apellidos,
                email = email, empresaId = companyId, rol = old?.rol ?: "ADMIN", passwordHash = "", passwordSalt = "",
                passwordIterations = 0, firebaseUid = identity.uid)
            val userId = if (old == null) dao.insertarUsuario(updated) else old.id.also { dao.actualizarUsuario(updated) }
            val extras = db.perfilDao().buscarPerfil(userId) ?: PerfilEntity(userId)
            db.perfilDao().guardarPerfil(extras.copy(telefono = profile.telefono, dni = profile.dni, cargo = profile.cargo, direccion = profile.direccion, telefonoRegion = profile.telefonoRegion))
            if (abrirSesion) dao.guardarSesion(SesionEntity(usuarioId = userId, expiraEn = Long.MAX_VALUE))
        }
    }

    override suspend fun cerrarSesion(): ResultadoAuth = ejecutar {
        identidadPendiente = null
        remote.salir() // Solo elimina las credenciales del SDK en este dispositivo.
        dao.borrarSesion()
        ResultadoAuth.Correcto
    }

    override suspend fun recuperarPassword(email: String): ResultadoAuth = ejecutar {
        if (AuthValidator.validarEmail(email).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        if (!remote.configurado) return@ejecutar ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        remote.enviarRecuperacion(AuthValidator.normalizarEmail(email))
        ResultadoAuth.Correcto
    }

    private suspend fun ejecutar(block: suspend () -> ResultadoAuth): ResultadoAuth = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: ErrorServicioAuth) { ResultadoAuth.Error(e.tipo) }
            catch (_: SQLiteConstraintException) { ResultadoAuth.Error(ErrorAuth.ALMACENAMIENTO) }
            catch (_: Exception) { ResultadoAuth.Error(ErrorAuth.ALMACENAMIENTO) }
        }
    }
}
