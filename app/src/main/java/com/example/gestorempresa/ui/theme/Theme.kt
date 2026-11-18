package com.example.gestorempresa.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = AzulPrincipal, onPrimary = Color.White,
    primaryContainer = AzulContenedor, onPrimaryContainer = AzulSobreContenedor,
    secondary = VerdeSecundario, onSecondary = Color.White, secondaryContainer = VerdeContenedor,
    background = FondoClaro, onBackground = TextoClaro,
    surface = Color.White, onSurface = TextoClaro,
    surfaceVariant = VarianteClara, onSurfaceVariant = TextoVarianteClaro,
    // Fondos de tarjetas, menús y barra inferior (sin ellos Material usa tonos lilas por defecto).
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAF7F2),
    surfaceContainer = VarianteClara, surfaceContainerHigh = Color(0xFFECE8E1), surfaceContainerHighest = Color(0xFFE6E1D9),
    outline = BordeClaro, error = ErrorClaro, onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = AzulPrincipalOscuro, onPrimary = SobreAzulOscuro,
    primaryContainer = AzulContenedorOscuro, onPrimaryContainer = AzulSobreContenedorOscuro,
    secondary = VerdeSecundarioOscuro, secondaryContainer = VerdeContenedorOscuro,
    background = FondoOscuro, onBackground = TextoOscuro,
    surface = SuperficieOscura, onSurface = TextoOscuro,
    surfaceVariant = VarianteOscura, onSurfaceVariant = TextoVarianteOscuro,
    surfaceContainerLowest = Color(0xFF121110), surfaceContainerLow = SuperficieOscura,
    surfaceContainer = VarianteOscura, surfaceContainerHigh = Color(0xFF35312D), surfaceContainerHighest = Color(0xFF403B36),
    outline = BordeOscuro, error = ErrorOscuro, onError = SobreErrorOscuro
)

/**
 * Indica a cualquier pantalla si la app está en modo oscuro.
 * Útil para colores propios que no vienen de MaterialTheme. Uso: `val oscuro = LocalTemaOscuro.current`
 */
val LocalTemaOscuro = staticCompositionLocalOf { false }

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

    CompositionLocalProvider(LocalTemaOscuro provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
