package com.example.gestorempresa.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.domain.auth.AuthValidator
import com.example.gestorempresa.domain.auth.CampoAuth
import com.example.gestorempresa.domain.auth.DatosCuenta

/** Recupera el perfil de una cuenta existente, sin otro registro ni otra contraseña. */
@Composable
fun CompleteAccountScreen(state: AuthUiState, onChange: (DatosCuenta) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val datos = state.datosCuenta
    val enabled = !state.ocupado
    val order = listOf(CampoAuth.NOMBRE, CampoAuth.APELLIDOS, CampoAuth.EMPRESA, CampoAuth.SECTOR)
    val bring = remember { order.associateWith { BringIntoViewRequester() } }
    val focus = remember { order.associateWith { FocusRequester() } }
    fun field(campo: CampoAuth) = Modifier.bringIntoViewRequester(bring.getValue(campo)).focusRequester(focus.getValue(campo))
    LaunchedEffect(state.intentoValidacion) {
        if (state.intentoValidacion > 0) order.firstOrNull { it in state.errores }?.let {
            focus.getValue(it).requestFocus()
            bring.getValue(it).bringIntoView()
        }
    }
    AuthLayout("Completa tu cuenta", "Tu correo y contraseña son correctos. Faltan los datos compartidos de tu cuenta y empresa.") {
        Text("Si conservas el dispositivo donde te registraste, abre allí la app actualizada para recuperar tus datos. También puedes completarlos aquí con tu misma cuenta. Los campos con * son obligatorios.")
        Text(state.email, style = MaterialTheme.typography.bodyMedium)
        AuthTextField(datos.nombre, { onChange(datos.copy(nombre = it)) }, "Nombre *", state.errores[CampoAuth.NOMBRE], enabled, modifier = field(CampoAuth.NOMBRE))
        AuthTextField(datos.apellidos, { onChange(datos.copy(apellidos = it)) }, "Apellidos *", state.errores[CampoAuth.APELLIDOS], enabled, modifier = field(CampoAuth.APELLIDOS))
        AuthTextField(datos.empresa, { onChange(datos.copy(empresa = it)) }, "Nombre de la empresa *", state.errores[CampoAuth.EMPRESA], enabled, modifier = field(CampoAuth.EMPRESA))
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = field(CampoAuth.SECTOR).fillMaxWidth(),
                border = BorderStroke(1.dp, if (CampoAuth.SECTOR in state.errores) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)) {
                Text(if (datos.sector.isEmpty()) "Selecciona el sector *" else "Sector: ${datos.sector}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AuthValidator.sectores.forEach { sector ->
                    DropdownMenuItem(text = { Text(sector) }, onClick = { onChange(datos.copy(sector = sector)); expanded = false })
                }
            }
        }
        state.errores[CampoAuth.SECTOR]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.mensaje?.let { AuthMessage(it) }
        AuthSubmit("Guardar datos y entrar", state.ocupado, onSave)
        TextButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Cancelar y volver al acceso") }
    }
}
