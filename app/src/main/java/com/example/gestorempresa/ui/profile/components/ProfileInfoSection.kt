package com.example.gestorempresa.ui.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Tarjeta con un título y una lista de filas (por ejemplo, "Datos personales"). */
@Composable
fun ProfileInfoSection(titulo: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
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

/** Una fila "etiqueta / valor". Si el valor está vacío muestra "Sin indicar". */
@Composable
fun ProfileInfoRow(etiqueta: String, valor: String, mostrarSeparador: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = valor.ifBlank { "Sin indicar" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (valor.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        if (mostrarSeparador) HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
