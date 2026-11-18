package com.example.gestorempresa

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.domain.settings.TemaApp
import com.example.gestorempresa.ui.auth.AuthViewModel
import com.example.gestorempresa.ui.navigation.AuthRoot
import com.example.gestorempresa.ui.navigation.MainScaffold
import com.example.gestorempresa.ui.profile.ProfileViewModel
import com.example.gestorempresa.ui.settings.SettingsViewModel
import com.example.gestorempresa.ui.theme.GestorEmpresaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // El tema elegido en Ajustes decide si la app se ve clara u oscura.
            val ajustes by settingsViewModel.ui.collectAsStateWithLifecycle()
            val temaOscuro = when (ajustes.ajustes.tema) {
                TemaApp.SISTEMA -> isSystemInDarkTheme()
                TemaApp.CLARO -> false
                TemaApp.OSCURO -> true
            }
            // Los iconos de la barra de estado (hora, batería...) se adaptan al tema.
            LaunchedEffect(temaOscuro) {
                val estilo = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { temaOscuro }
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }

            GestorEmpresaTheme(darkTheme = temaOscuro) {
                AuthRoot(
                    viewModel = authViewModel,
                    contenidoAutenticado = { session, onLogout ->
                        MainScaffold(
                            sesion = session,
                            onCerrarSesion = onLogout,
                            profileViewModel = profileViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                )
            }
        }
    }
}
