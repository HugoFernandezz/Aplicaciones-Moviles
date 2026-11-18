package com.example.gestorempresa.ui.profile.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Foto redonda del administrador.
 * Si no tiene foto, muestra un círculo de color con sus iniciales (avatar por defecto).
 */
@Composable
fun ProfileAvatar(
    fotoRuta: String?,
    iniciales: String,
    modifier: Modifier = Modifier,
    tamano: Dp = 112.dp,
    onClick: (() -> Unit)? = null
) {
    val foto = rememberFoto(fotoRuta)
    val clic = if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Cambiar foto", onClick = onClick) else Modifier

    Box(modifier.size(tamano), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier.size(tamano).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).then(clic),
            contentAlignment = Alignment.Center
        ) {
            if (foto != null) {
                Image(foto, contentDescription = "Foto de perfil", contentScale = ContentScale.Crop, modifier = Modifier.size(tamano))
            } else {
                Text(
                    text = iniciales.ifEmpty { "?" },
                    fontSize = (tamano.value / 2.6f).sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        // Pequeña insignia con un lápiz para indicar que la foto se puede cambiar.
        if (onClick != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 2.dp) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(6.dp).size(18.dp))
            }
        }
    }
}

/** Carga la imagen del disco en segundo plano para no bloquear la pantalla. */
@Composable
private fun rememberFoto(ruta: String?): ImageBitmap? {
    val foto by produceState<ImageBitmap?>(initialValue = null, ruta) {
        value = ruta?.let { withContext(Dispatchers.IO) { decodificarReducida(it) } }
    }
    return foto
}

/**
 * Lee la imagen reduciendo su tamaño: una foto de móvil puede ocupar 4000 px,
 * pero el avatar solo necesita unos 512 px. Así se ahorra memoria.
 */
private fun decodificarReducida(ruta: String, ladoMaximo: Int = 512): ImageBitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(ruta, medidas)
    var escala = 1
    while (medidas.outWidth / (escala * 2) >= ladoMaximo && medidas.outHeight / (escala * 2) >= ladoMaximo) escala *= 2
    return BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = escala })?.asImageBitmap()
}
