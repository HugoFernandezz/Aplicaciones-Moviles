package com.example.gestorempresa.auth

import android.util.Log
import android.net.http.X509TrustManagerExtensions
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.android.gms.tasks.Tasks
import java.net.URL
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Diagnóstico optativo contra el proveedor real: no crea cuentas ni usa credenciales personales. */
class FirebaseConnectionTest {
    private fun optIn() = assumeTrue(InstrumentationRegistry.getArguments().getString("liveFirebase") == "true")

    @Test fun cadenaHttpsDeFirebaseEsValida() {
        optIn()
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
        val normal = factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        val hostnameAware = X509TrustManagerExtensions(normal)
        var currentHost = "identitytoolkit.googleapis.com"
        val diagnostic = object : X509TrustManager {
            override fun getAcceptedIssuers() = normal.acceptedIssuers
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = normal.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                chain.forEach { Log.i("FirebaseTlsDiagnostic", "subject=${it.subjectX500Principal}; issuer=${it.issuerX500Principal}") }
                // Conserva la validación normal: nunca acepta una cadena inválida.
                hostnameAware.checkServerTrusted(chain, authType, currentHost)
            }
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(diagnostic), null) }
        for (host in listOf("identitytoolkit.googleapis.com", "www.googleapis.com", "securetoken.googleapis.com", "firestore.googleapis.com")) {
            currentHost = host
            Log.i("FirebaseTlsDiagnostic", "host=$host")
            val connection = URL("https://$host/").openConnection() as HttpsURLConnection
            connection.connectTimeout = 20_000; connection.readTimeout = 20_000
            connection.sslSocketFactory = tls.socketFactory
            try { assertTrue(connection.responseCode in 200..499) } finally { connection.disconnect() }
        }
    }

    @Test fun proveedorRealRespondeCredencialesInvalidasParaCuentaFicticia() {
        optIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = requireNotNull(FirebaseApp.initializeApp(context)).options
        val app = FirebaseApp.initializeApp(context, options, "diagnostico-acceso-${System.nanoTime()}")
        try {
            try {
                Tasks.await(FirebaseAuth.getInstance(app).signInWithEmailAndPassword(
                    "diagnostico-acceso@example.invalid", "diagnostico-sin-cuenta-2026"), 30, TimeUnit.SECONDS)
                error("La cuenta ficticia no debe iniciar sesión")
            } catch (e: ExecutionException) {
                assertTrue("El SDK debe alcanzar Firebase y recibir un rechazo de credenciales: ${e.cause?.message}",
                    e.cause is FirebaseAuthInvalidCredentialsException)
            }
        } finally { app.delete() }
    }
}
