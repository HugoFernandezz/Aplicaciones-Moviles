package com.example.gestorempresa.data.auth

import com.example.gestorempresa.BuildConfig
import com.example.gestorempresa.domain.auth.*
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Usa la misma FirebaseApp (y por tanto el mismo usuario) que Authentication. */
@Singleton
class FirestorePerfilCuentaService @Inject constructor(
    private val identidad: FirebaseEmailAuthService
) : PerfilCuentaService {
    private val db by lazy {
        val app = identidad.firebaseApp() ?: throw ErrorServicioAuth(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            if (BuildConfig.DEBUG && BuildConfig.AUTH_EMULATOR_HOST.isNotBlank()) {
                useEmulator(BuildConfig.AUTH_EMULATOR_HOST, 8080)
            }
        }
    }

    override suspend fun obtener(uid: String): PerfilCuentaRemoto? = llamar {
        val doc = db.collection("authProfiles").document(uid).get(Source.SERVER).esperar()
        if (doc.exists()) doc.perfil() else null
    }

    override suspend fun crearSiFalta(perfil: PerfilCuentaRemoto): PerfilCuentaRemoto = llamar {
        val ref = db.collection("authProfiles").document(perfil.uid)
        db.runTransaction { tx ->
            val actual = tx.get(ref)
            if (actual.exists()) actual.perfil()
            else perfil.also { tx.set(ref, it.campos()) }
        }.esperar()
    }

    override suspend fun actualizar(perfil: PerfilCuentaRemoto) { llamar {
        // update exige que exista: una edición nunca crea una identidad o empresa nuevas.
        db.collection("authProfiles").document(perfil.uid).update(perfil.campos()).esperar()
    } }

    private fun PerfilCuentaRemoto.campos(): Map<String, Any> = mapOf(
        "nombre" to nombre, "apellidos" to apellidos, "empresaId" to empresaId,
        "empresaNombre" to empresaNombre, "sector" to sector, "creadaEn" to creadaEn,
        "telefono" to telefono, "dni" to dni, "cargo" to cargo, "direccion" to direccion,
        "telefonoRegion" to (telefonoRegion ?: com.example.gestorempresa.domain.phone.InternationalPhones.restore(telefono).region)
    )

    private fun DocumentSnapshot.perfil() = PerfilCuentaRemoto(
        uid = id, nombre = texto("nombre"), apellidos = texto("apellidos"),
        empresaId = texto("empresaId"), empresaNombre = texto("empresaNombre"),
        sector = texto("sector"), creadaEn = getLong("creadaEn") ?: throw ErrorServicioAuth(ErrorAuth.PERFIL_NO_DISPONIBLE),
        telefono = getString("telefono").orEmpty(), dni = getString("dni").orEmpty(), cargo = getString("cargo").orEmpty(),
        direccion = getString("direccion").orEmpty(), telefonoRegion = getString("telefonoRegion")
    ).also {
        if (it.empresaId != it.uid) throw ErrorServicioAuth(ErrorAuth.PERFIL_NO_DISPONIBLE)
    }
    private fun DocumentSnapshot.texto(key: String) = getString(key) ?: throw ErrorServicioAuth(ErrorAuth.PERFIL_NO_DISPONIBLE)

    private suspend fun <T> llamar(block: suspend () -> T): T = try { withTimeout(20_000) { block() } }
    catch (_: TimeoutCancellationException) { throw ErrorServicioAuth(ErrorAuth.SIN_CONEXION) }
    catch (e: CancellationException) { throw e }
    catch (e: ErrorServicioAuth) { throw e }
    catch (e: FirebaseFirestoreException) {
        throw ErrorServicioAuth(when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ErrorAuth.SIN_CONEXION
            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.FAILED_PRECONDITION -> ErrorAuth.SERVICIO_NO_CONFIGURADO
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> ErrorAuth.CREDENCIALES_INVALIDAS
            FirebaseFirestoreException.Code.NOT_FOUND -> ErrorAuth.PERFIL_NO_DISPONIBLE
            else -> ErrorAuth.SERVICIO_NO_DISPONIBLE
        })
    }

    private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Firestore no disponible"))
        } }
    }
}
