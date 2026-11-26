package com.example.gestorempresa.ui.advanced.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.gestorempresa.data.media.CropFrame
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Solo archivos temporales. La foto definitiva la guarda el repositorio del perfil. */
class PhotoAdjustmentStore(private val context: Context) {
    private val folder get() = File(context.cacheDir, "ajuste_fotos").apply { mkdirs() }
    suspend fun prepare(source: PhotoSource): File = withContext(Dispatchers.IO) {
        when (source) {
            is PhotoSource.Camera -> source.file.also { require(it.isFile && it.length() > 0) }
            is PhotoSource.Gallery -> {
                val destination = File(folder, "origen_${UUID.randomUUID()}")
                try {
                    val input = requireNotNull(context.contentResolver.openInputStream(source.uri))
                    input.use { from -> destination.outputStream().use { to ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = from.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 30L * 1024 * 1024) { "Imagen demasiado grande" }
                            to.write(buffer, 0, count)
                        }
                    } }
                    destination
                } catch (e: Exception) { destination.delete(); throw e }
            }
        }
    }
    suspend fun crop(bitmap: Bitmap, frame: CropFrame): File = withContext(Dispatchers.IO) {
        val bounds = frame.bounds(bitmap.width, bitmap.height)
        val cut = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.side, bounds.side)
        val resized = Bitmap.createScaledBitmap(cut, 768, 768, true)
        val output = File(folder, "recorte_${UUID.randomUUID()}.jpg")
        try {
            output.outputStream().use { check(resized.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            output
        } catch (e: Exception) { output.delete(); throw e }
    }
    fun discard(path: String?) {
        val file = path?.let(::File) ?: return
        val parent = file.canonicalFile.parentFile
        if (parent == folder.canonicalFile || parent == File(context.cacheDir, "camara").canonicalFile) file.delete()
    }
    fun uri(file: File): Uri = Uri.fromFile(file)
}
