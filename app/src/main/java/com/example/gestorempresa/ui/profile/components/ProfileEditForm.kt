package com.example.gestorempresa.ui.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.domain.auth.AuthValidator
import com.example.gestorempresa.domain.profile.CampoPerfil
import com.example.gestorempresa.domain.profile.DatosPerfil

/**
 * Formulario para modificar el perfil.
 * No guarda nada por sí mismo: avisa con [onCambio] cada vez que el usuario escribe
 * y con [onGuardar] / [onCancelar] al pulsar los botones.
 */
@Composable
fun ProfileEditForm(
    datos: DatosPerfil,
    errores: Map<CampoPerfil, String>,
    email: String,
    guardando: Boolean,
    onCambio: (DatosPerfil) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Datos personales", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        CampoTexto("Nombre", datos.nombre, errores[CampoPerfil.NOMBRE], guardando) { onCambio(datos.copy(nombre = it)) }
        CampoTexto("Apellidos", datos.apellidos, errores[CampoPerfil.APELLIDOS], guardando) { onCambio(datos.copy(apellidos = it)) }
        CampoTexto("Teléfono (opcional)", datos.telefono, errores[CampoPerfil.TELEFONO], guardando, KeyboardType.Phone) { onCambio(datos.copy(telefono = it)) }
        CampoTexto("DNI / NIE (opcional)", datos.dni, errores[CampoPerfil.DNI], guardando, capitalizar = KeyboardCapitalization.Characters) { onCambio(datos.copy(dni = it)) }
        CampoTexto("Cargo en la empresa (opcional)", datos.cargo, errores[CampoPerfil.CARGO], guardando) { onCambio(datos.copy(cargo = it)) }

        Text("Empresa", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        CampoTexto("Nombre de la empresa", datos.empresaNombre, errores[CampoPerfil.EMPRESA], guardando) { onCambio(datos.copy(empresaNombre = it)) }
        SelectorSector(datos.sector, errores[CampoPerfil.SECTOR], guardando) { onCambio(datos.copy(sector = it)) }

        // El correo identifica la cuenta en el servicio de acceso, por eso no se puede cambiar aquí.
        OutlinedTextField(
            value = email, onValueChange = {}, label = { Text("Correo electrónico") },
            enabled = false, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("El correo de la cuenta no se puede modificar.") }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancelar, enabled = !guardando, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(onClick = onGuardar, enabled = !guardando, modifier = Modifier.weight(1f)) {
                Text(if (guardando) "Guardando…" else "Guardar")
            }
        }
    }
}

/** Campo de texto con su mensaje de error debajo. */
@Composable
private fun CampoTexto(
    etiqueta: String,
    valor: String,
    error: String?,
    bloqueado: Boolean,
    teclado: KeyboardType = KeyboardType.Text,
    capitalizar: KeyboardCapitalization = KeyboardCapitalization.Words,
    onCambio: (String) -> Unit
) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, label = { Text(etiqueta) },
        enabled = !bloqueado, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = error != null, supportingText = { if (error != null) Text(error) },
        keyboardOptions = KeyboardOptions(capitalization = capitalizar, keyboardType = teclado, imeAction = ImeAction.Next)
    )
}

/** Desplegable con los mismos sectores que se ofrecen en el Registro. */
@Composable
private fun SelectorSector(sector: String, error: String?, bloqueado: Boolean, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { abierto = true }, enabled = !bloqueado, modifier = Modifier.fillMaxWidth()) {
            Text(if (sector.isEmpty()) "Selecciona el sector" else "Sector: $sector")
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            AuthValidator.sectores.forEach { opcion ->
                DropdownMenuItem(text = { Text(opcion) }, onClick = { onElegir(opcion); abierto = false })
            }
        }
    }
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
