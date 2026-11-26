package com.example.gestorempresa.auth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.gestorempresa.domain.auth.AuthValidator
import com.example.gestorempresa.domain.auth.CampoAuth
import com.example.gestorempresa.ui.auth.AuthUiState
import com.example.gestorempresa.ui.auth.RegisterScreen
import com.example.gestorempresa.ui.theme.GestorEmpresaTheme
import org.junit.Rule
import org.junit.Test

class RegisterValidationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enviarDesdeElFinalDesplazaYEnfocaElPrimerCampoInvalido() {
        val state = mutableStateOf(AuthUiState())
        compose.setContent {
            GestorEmpresaTheme {
                RegisterScreen(state.value, { state.value = state.value.copy(registro = it) }, {
                    state.value = state.value.copy(errores = AuthValidator.validarRegistro(state.value.registro),
                        intentoValidacion = state.value.intentoValidacion + 1)
                }, {})
            }
        }
        compose.onNodeWithText("Crear cuenta y empresa").performScrollTo().performClick()
        compose.onNodeWithText("Nombre").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("El nombre debe tener entre 2 y 60 caracteres.").assertIsDisplayed()
        compose.runOnIdle {
            check(CampoAuth.SECTOR in state.value.errores)
            check(CampoAuth.CONFIRMACION in state.value.errores)
        }
        compose.onNodeWithText("Nombre").performTextReplacement("Ana")
        compose.onNodeWithText("Crear cuenta y empresa").performScrollTo().performClick()
        compose.onNodeWithText("Apellidos").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("Nombre").assertTextContains("Ana")
    }
}
