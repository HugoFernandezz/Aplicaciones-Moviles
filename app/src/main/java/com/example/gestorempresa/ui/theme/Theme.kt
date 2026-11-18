package com.example.gestorempresa.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = AzulPrincipal, onPrimary = Color.White,
    primaryContainer = AzulContenedor, onPrimaryContainer = AzulSobreContenedor,
    secondary = VerdeSecundario, onSecondary = Color.White, secondaryContainer = VerdeContenedor,
    background = FondoClaro, onBackground = TextoClaro,
    surface = Color.White, onSurface = TextoClaro,
    surfaceVariant = VarianteClara, onSurfaceVariant = TextoVarianteClaro,
    surfaceContainer = VarianteClara, // fondo de la barra de navegación inferior
    outline = BordeClaro, error = ErrorClaro, onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = AzulPrincipalOscuro, onPrimary = SobreAzulOscuro,
    primaryContainer = AzulContenedorOscuro, onPrimaryContainer = AzulSobreContenedorOscuro,
    secondary = VerdeSecundarioOscuro, secondaryContainer = VerdeContenedorOscuro,
    background = FondoOscuro, onBackground = TextoOscuro,
    surface = SuperficieOscura, onSurface = TextoOscuro,
    surfaceVariant = VarianteOscura, onSurfaceVariant = TextoVarianteOscuro,
    surfaceContainer = VarianteOscura, // fondo de la barra de navegación inferior
    outline = BordeOscuro, error = ErrorOscuro, onError = SobreErrorOscuro
)

/**
 * Tema general de la aplicación.
 * [darkTheme] lo decide MainActivity según la preferencia elegida en Ajustes.
 */
@Composable
fun GestorEmpresaTheme(
    darkTheme: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
