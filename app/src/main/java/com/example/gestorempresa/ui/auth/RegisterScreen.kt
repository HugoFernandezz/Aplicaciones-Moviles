package com.example.gestorempresa.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import com.example.gestorempresa.domain.auth.AuthValidator
import com.example.gestorempresa.domain.auth.CampoAuth
import com.example.gestorempresa.domain.auth.RegistroUsuario
import com.example.gestorempresa.ui.theme.GestorEmpresaTheme

@Composable
fun RegisterScreen(state: AuthUiState, onChange: (RegistroUsuario) -> Unit, onRegister: () -> Unit, onLogin: () -> Unit) {
    val r = state.registro
    val enabled = !state.ocupado
    val order = listOf(CampoAuth.NOMBRE, CampoAuth.APELLIDOS, CampoAuth.EMAIL,
        CampoAuth.EMPRESA, CampoAuth.SECTOR, CampoAuth.PASSWORD, CampoAuth.CONFIRMACION)
    val bring = remember { order.associateWith { BringIntoViewRequester() } }
    val focus = remember { order.associateWith { FocusRequester() } }
    fun field(campo: CampoAuth) = Modifier.bringIntoViewRequester(bring.getValue(campo)).focusRequester(focus.getValue(campo))
    LaunchedEffect(state.intentoValidacion) {
        if (state.intentoValidacion > 0) order.firstOrNull { it in state.errores }?.let { first ->
            focus.getValue(first).requestFocus()
            bring.getValue(first).bringIntoView()
        }
    }
    AuthLayout("Crea tu cuenta", "Registra tu empresa y empieza a gestionarla como administrador.") {
        AuthTextField(r.nombre, { onChange(r.copy(nombre = it)) }, "Nombre", state.errores[CampoAuth.NOMBRE], enabled, modifier = field(CampoAuth.NOMBRE))
        AuthTextField(r.apellidos, { onChange(r.copy(apellidos = it)) }, "Apellidos", state.errores[CampoAuth.APELLIDOS], enabled, modifier = field(CampoAuth.APELLIDOS))
        AuthTextField(r.email, { onChange(r.copy(email = it)) }, "Correo electrónico", state.errores[CampoAuth.EMAIL], enabled, KeyboardType.Email, modifier = field(CampoAuth.EMAIL))
        AuthTextField(r.empresa, { onChange(r.copy(empresa = it)) }, "Nombre de la empresa", state.errores[CampoAuth.EMPRESA], enabled, modifier = field(CampoAuth.EMPRESA))
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = field(CampoAuth.SECTOR).fillMaxWidth(),
                border = BorderStroke(1.dp, if (CampoAuth.SECTOR in state.errores) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)) {
                Text(if (r.sector.isEmpty()) "Selecciona el sector de tu empresa" else "Sector: ${r.sector}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AuthValidator.sectores.forEach { sector ->
                    DropdownMenuItem(text = { Text(sector) }, onClick = { onChange(r.copy(sector = sector)); expanded = false })
                }
            }
        }
        state.errores[CampoAuth.SECTOR]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AuthPasswordField(r.password, { onChange(r.copy(password = it)) }, "Contraseña", state.errores[CampoAuth.PASSWORD], enabled, modifier = field(CampoAuth.PASSWORD))
        Text("Entre 12 y 128 caracteres. Puedes usar una frase fácil de recordar.", style = MaterialTheme.typography.bodySmall)
        AuthPasswordField(r.confirmarPassword, { onChange(r.copy(confirmarPassword = it)) }, "Repite la contraseña", state.errores[CampoAuth.CONFIRMACION], enabled, done = true, onDone = onRegister, modifier = field(CampoAuth.CONFIRMACION))
        state.mensaje?.let { AuthMessage(it) }
        AuthSubmit("Crear cuenta y empresa", state.ocupado, onRegister)
        TextButton(onClick = onLogin, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Ya tengo cuenta. Iniciar sesión") }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun RegisterPreview() { GestorEmpresaTheme { RegisterScreen(AuthUiState(), {}, {}, {}) } }
