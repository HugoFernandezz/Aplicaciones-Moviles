package com.example.gestorempresa.ui.home

import androidx.compose.runtime.Composable

/**
 * El módulo de Inicio usa el tema general de la app (ui/theme/GestorEmpresaTheme),
 * que ya tiene esta misma paleta en claro y además una versión oscura.
 * Así Inicio respeta el tema claro/oscuro elegido en Ajustes.
 */
@Composable
fun HomeModuleTheme(content: @Composable () -> Unit) = content()
