package com.example.gestorempresa.data.profile

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guarda las fotos de perfil en el almacenamiento interno de la app.
 *
 * ¿Por qué copiarlas? El selector de imágenes devuelve un enlace temporal (content://...)
 * que puede dejar de funcionar al cerrar la app. Copiando el archivo, la foto sigue
 * disponible aunque el usuario la borre de su galería.
 */
@Singleton
class FotoPerfilStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private val carpeta: File get() = File(context.filesDir, "fotos_perfil").apply { mkdirs() }

    /** Copia la imagen y devuelve la ruta del nuevo archivo. */
    suspend fun guardar(usuarioId: Long, uriImagen: String): String = withContext(Dispatchers.IO) {
        // La hora en el nombre hace que cada foto nueva tenga una ruta distinta y la UI la recargue.
        val destino = File(carpeta, "usuario_${usuarioId}_${System.currentTimeMillis()}.jpg")
        val entrada = context.contentResolver.openInputStream(Uri.parse(uriImagen))
            ?: error("No se ha podido abrir la imagen seleccionada")
        entrada.use { input -> destino.outputStream().use { output -> input.copyTo(output) } }
        destino.absolutePath
    }

    /** Borra una foto anterior. Solo borra archivos de nuestra carpeta, nunca otros. */
    suspend fun borrar(ruta: String?) = withContext(Dispatchers.IO) {
        val archivo = ruta?.let(::File) ?: return@withContext
        if (archivo.parentFile == carpeta) archivo.delete()
    }
}
