package com.example.gestorempresa.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import com.example.gestorempresa.ui.navigation.OpcionMenu

/**
 * Opciones del menú lateral de Inicio.
 * Las rutas son las mismas que ya usaba HomeListsContent para cambiar de sección.
 */
fun homeMenuOptions(avisos: Int = 0, avisosUrgentes: Boolean = false): List<OpcionMenu> = listOf(
    OpcionMenu("home", "Inicio", Icons.Outlined.Home),
    // Gestión de la empresa
    OpcionMenu(CatalogKind.EMPLOYEES.name, CatalogKind.EMPLOYEES.title, Icons.Outlined.Person, separadorAntes = true),
    OpcionMenu(CatalogKind.SUBCONTRACTORS.name, CatalogKind.SUBCONTRACTORS.title, Icons.Outlined.AccountBox),
    OpcionMenu(CatalogKind.PROJECTS.name, CatalogKind.PROJECTS.title, Icons.Outlined.Build),
    OpcionMenu(CatalogKind.SUPPLIERS.name, CatalogKind.SUPPLIERS.title, Icons.Outlined.ShoppingCart),
    OpcionMenu(CatalogKind.MATERIALS.name, CatalogKind.MATERIALS.title, Icons.AutoMirrored.Outlined.List),
    // Seguimiento y comunicación
    OpcionMenu("obligations", "Avisos", Icons.Outlined.Notifications, contador = avisos, urgente = avisosUrgentes, separadorAntes = true),
    OpcionMenu("tasks", "Tareas", Icons.Outlined.CheckCircle),
    OpcionMenu("assignments", "Zonas laborales", Icons.Outlined.LocationOn),
    OpcionMenu("messaging", "Mensajería", Icons.Outlined.Email)
)

/** Título que se muestra en la barra superior para la sección actual. */
fun homeMenuTitle(destination: String): String =
    homeMenuOptions().firstOrNull { it.ruta == destination }?.titulo ?: "Inicio"
