package com.example.gestorempresa.ui.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.domain.auth.AuthValidator
import com.example.gestorempresa.domain.profile.*
import com.example.gestorempresa.ui.home.InternationalPhoneField

@Composable
fun ProfileEditForm(
    datos: DatosPerfil, errores: Map<CampoPerfil, String>, email: String, guardando: Boolean,
    onCambio: (DatosPerfil) -> Unit, onGuardar: () -> Unit, onCancelar: () -> Unit,
    intentoValidacion: Int = 0
) {
    val order = listOf(CampoPerfil.NOMBRE, CampoPerfil.APELLIDOS, CampoPerfil.TELEFONO, CampoPerfil.DIRECCION,
        CampoPerfil.DNI, CampoPerfil.CARGO, CampoPerfil.EMPRESA, CampoPerfil.SECTOR)
    val focus = remember { order.associateWith { FocusRequester() } }
    val bring = remember { order.associateWith { BringIntoViewRequester() } }
    fun field(c: CampoPerfil) = Modifier.focusRequester(focus.getValue(c)).bringIntoViewRequester(bring.getValue(c))
    LaunchedEffect(intentoValidacion) {
        if (intentoValidacion > 0) order.firstOrNull { it in errores }?.let {
            focus.getValue(it).requestFocus()
            bring.getValue(it).bringIntoView()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mis datos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("* Campo obligatorio", style = MaterialTheme.typography.bodySmall)
        CampoTexto("Nombre *", datos.nombre, errores[CampoPerfil.NOMBRE], guardando, field(CampoPerfil.NOMBRE)) { onCambio(datos.copy(nombre = it)) }
        CampoTexto("Apellidos *", datos.apellidos, errores[CampoPerfil.APELLIDOS], guardando, field(CampoPerfil.APELLIDOS)) { onCambio(datos.copy(apellidos = it)) }
        InternationalPhoneField(datos.telefonoRegion, datos.telefono,
            { onCambio(datos.copy(telefono = it.number, telefonoRegion = it.region)) }, !guardando,
            CampoPerfil.TELEFONO in errores, field(CampoPerfil.TELEFONO),
            required = false, tagPrefix = "profile-phone", inline = true)
        CampoTexto("Dirección (opcional)", datos.direccion, errores[CampoPerfil.DIRECCION], guardando, field(CampoPerfil.DIRECCION)) { onCambio(datos.copy(direccion = it)) }
        CampoTexto("DNI / NIE (opcional)", datos.dni, errores[CampoPerfil.DNI], guardando, field(CampoPerfil.DNI)) { onCambio(datos.copy(dni = it)) }
        CampoTexto("Cargo (opcional)", datos.cargo, errores[CampoPerfil.CARGO], guardando, field(CampoPerfil.CARGO)) { onCambio(datos.copy(cargo = it)) }
        Text("Empresa", style = MaterialTheme.typography.titleMedium)
        CampoTexto("Nombre de la empresa *", datos.empresaNombre, errores[CampoPerfil.EMPRESA], guardando, field(CampoPerfil.EMPRESA)) { onCambio(datos.copy(empresaNombre = it)) }
        var expanded by remember { mutableStateOf(false) }
        val sectorError = errores[CampoPerfil.SECTOR]
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = !guardando, modifier = field(CampoPerfil.SECTOR).fillMaxWidth(),
                border = BorderStroke(1.dp, if (sectorError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)) {
                Text(if (datos.sector.isBlank()) "Sector *: seleccionar" else "Sector *: ${datos.sector}")
            }
            DropdownMenu(expanded, { expanded = false }) {
                AuthValidator.sectores.forEach { sector ->
                    DropdownMenuItem(text = { Text(sector) }, onClick = { onCambio(datos.copy(sector = sector)); expanded = false })
                }
            }
        }
        sectorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(email, {}, label = { Text("Correo electrónico") }, readOnly = true, enabled = false,
            modifier = Modifier.fillMaxWidth(), supportingText = { Text("El correo identifica tu cuenta y no se modifica aquí.") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancelar, enabled = !guardando, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(onClick = onGuardar, enabled = !guardando, modifier = Modifier.weight(1f).testTag("profile-save")) {
                Text(if (guardando) "Guardando…" else "Guardar")
            }
        }
    }
}

@Composable
private fun CampoTexto(label: String, value: String, error: String?, busy: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = !busy, singleLine = true,
        isError = error != null, supportingText = { error?.let { Text(it) } },
        modifier = modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
}
