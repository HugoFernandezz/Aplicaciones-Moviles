package com.example.gestorempresa.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Tarjeta con el título de una sección de Ajustes (Notificaciones, Preferencias...). */
@Composable
fun SettingsSection(titulo: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            contenido()
        }
    }
}

/** Fila con un texto y un interruptor. Se puede pulsar en toda la fila, no solo en el interruptor. */
@Composable
fun SettingsSwitchRow(titulo: String, descripcion: String, activado: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = activado, role = Role.Switch, onValueChange = onCambio),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // onCheckedChange = null porque el clic ya lo gestiona la fila completa.
        Switch(checked = activado, onCheckedChange = null)
    }
}

/** Fila con un botón redondo para elegir una sola opción de una lista (por ejemplo, el tema). */
@Composable
fun SettingsRadioRow(titulo: String, seleccionado: Boolean, onElegir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = seleccionado, role = Role.RadioButton, onClick = onElegir).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionado, onClick = null)
        Text(titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Fila de solo lectura "etiqueta: valor". */
@Composable
fun SettingsInfoRow(etiqueta: String, valor: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}
