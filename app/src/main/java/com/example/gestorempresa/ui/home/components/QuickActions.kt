package com.example.gestorempresa.ui.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.theme.LocalTemaOscuro

@Composable
fun QuickActions(
    onNuevaObra: () -> Unit,
    onAltaEmpleado: () -> Unit,
    onMovimiento: () -> Unit,
    onParteDiario: () -> Unit,
    modifier: Modifier = Modifier
) {
    // En modo oscuro se usan tonos más claros para que el texto se lea sobre fondo oscuro.
    val oscuro = LocalTemaOscuro.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ACCIONES RÁPIDAS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.horizontalScroll(
                rememberScrollState()
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                texto = "+ Nuevo servicio",
                color = if (oscuro) Color(0xFFFFB77C) else Color(0xFF9A4C00),
                onClick = onNuevaObra
            )

            QuickActionButton(
                texto = "+ Alta empleado",
                color = if (oscuro) Color(0xFF9DC7EC) else Color(0xFF285E8E),
                onClick = onAltaEmpleado
            )

            QuickActionButton(
                texto = "Gasto / Ingreso",
                color = if (oscuro) Color(0xFF8FD6B0) else Color(0xFF22684A),
                onClick = onMovimiento
            )

            QuickActionButton(
                texto = "Parte diario",
                color = if (oscuro) Color(0xFFD3B5F5) else Color(0xFF67408C),
                onClick = onParteDiario
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    texto: String,
    color: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = texto,
            color = color
        )
    }
}

