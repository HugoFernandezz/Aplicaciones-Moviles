package com.example.gestorempresa.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.gestorempresa.data.media.decodePhotoBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class FotoPerfilStorage @Inject constructor(@ApplicationContext private val context: Context) {
    private val carpeta get() = File(context.filesDir, "fotos_perfil").apply { mkdirs() }

    suspend fun guardar(usuarioId: Long, uriImagen: String): String = withContext(Dispatchers.IO) {
        val usuario = File(carpeta, "usuario_$usuarioId").apply { mkdirs() }
        val original = File(usuario, "entrada_${UUID.randomUUID()}.tmp")
        val destino = File(usuario, "foto_${UUID.randomUUID()}.jpg")
        try {
            requireNotNull(context.contentResolver.openInputStream(Uri.parse(uriImagen))).use { input ->
                original.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 30L * 1024 * 1024) { "Imagen demasiado grande" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bitmap = requireNotNull(decodePhotoBitmap(original.path, 1024)) { "El archivo no es una imagen válida" }
            destino.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            destino.absolutePath
        } catch (e: Exception) { destino.delete(); throw e }
        finally { original.delete() }
    }

    fun pertenece(usuarioId: Long, ruta: String): Boolean = runCatching {
        val file = File(ruta).canonicalFile
        val root = carpeta.canonicalFile
        file.parentFile == File(root, "usuario_$usuarioId") ||
            (file.parentFile == root && file.name.startsWith("usuario_${usuarioId}_"))
    }.getOrDefault(false)

    suspend fun borrar(ruta: String?) = withContext(Dispatchers.IO) {
        val file = ruta?.let(::File)?.canonicalFile ?: return@withContext
        if (file.path.startsWith(carpeta.canonicalPath + File.separator)) file.delete()
    }
}
