package com.example.gestorempresa.phone

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gestorempresa.data.local.HomeDatabase
import com.example.gestorempresa.data.local.entities.EmpleadoEntity
import com.example.gestorempresa.ui.home.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class EmployeePhoneUiTest {
    @Test fun dniConFormatoCorrectoYLetraIncorrectaExplicaElErrorSinCambiarElDocumento() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        try {
            compose.setContent { HomeModuleTheme { HomeEntryForm("employee-new", vm, HomeWorkspaceState(loading = false), { saved.set(true) }) } }
            fillEmployee()
            fill("DNI / NIE *", "12345678N")
            compose.onNodeWithTag("employee-phone-number").performScrollTo().performTextReplacement("612345678")
            save()
            compose.onNodeWithText("DNI / NIE *").assertIsFocused().assertTextContains("12345678N")
            compose.onAllNodesWithText("El formato es correcto", substring = true).onFirst().assertExists()
            assertFalse(saved.get())
            assertTrue(runBlocking { db.homeDao().employees().first().isEmpty() })
            fill("DNI / NIE *", "12345678Z")
            save(); compose.waitUntil(10_000) { saved.get() }
            assertEquals("12345678Z", runBlocking { db.homeDao().employees().first().single().dni })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fill(label: String, value: String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun save() = compose.onNodeWithText("Guardar").performScrollTo().performClick()
    private fun fillEmployee() {
        fill("Nombre *", "Ana"); fill("Apellidos *", "Pérez"); fill("DNI / NIE *", "12345678Z")
        fill("Oficio *", "Electricista"); fill("Correo electrónico *", "ana@example.invalid")
        fill("Salario base mensual (€) *", "1500"); fill("Nº de Seguridad Social *", "123456789012")
        fill("Lugar de residencia *", "Murcia"); fill("Dirección personal *", "Calle Mayor 1")
    }

    @Test fun altaEspanolaMuestraPrefijoInicialYGuardaE164() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        try {
            compose.setContent { HomeModuleTheme { HomeEntryForm("employee-new", vm, HomeWorkspaceState(loading = false), { saved.set(true) }) } }
            compose.onNodeWithTag("employee-phone-country").performScrollTo().assertTextContains("🇪🇸 +34", substring = true)
            fillEmployee()
            compose.onNodeWithTag("employee-phone-number").performScrollTo().performTextReplacement("612 345 678")
            save(); compose.waitUntil(10_000) { saved.get() }
            assertEquals("+34612345678", runBlocking { db.homeDao().employees().first().single().telefono })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }

    @Test fun seleccionDeOtroPaisPersisteYPermiteTelefonoDeOchoDigitos() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent { HomeModuleTheme { HomeEntryForm("employee-new", vm, HomeWorkspaceState(loading = false), { saved.set(true) }) } }
            fillEmployee()
            compose.onNodeWithTag("employee-phone-country").performScrollTo().performClick()
            compose.onNodeWithTag("phone-country-search").performTextInput("Dinamarca")
            compose.onNodeWithTag("phone-country-DK").performClick()
            compose.onNodeWithTag("employee-phone-number").performScrollTo().performTextReplacement("32123456")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("employee-phone-country").performScrollTo().assertTextContains("🇩🇰 +45", substring = true)
            compose.onNodeWithTag("employee-phone-number").assertTextContains("32123456")
            save(); compose.waitUntil(10_000) { saved.get() }
            assertEquals("+4532123456", runBlocking { db.homeDao().employees().first().single().telefono })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }

    @Test fun editarNumeroExtranjeroConservaPrefijoYBloqueaNumeroInvalido() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        val original = EmpleadoEntity(nombre = "Ana", apellidos = "Pérez", dni = "12345678Z", telefono = "+442079460018",
            email = "ana@example.invalid", oficio = "Electricista", tipoEmpleado = "COMPLETO", salarioBase = 1500.0,
            lugarResidencia = "Murcia", direccionPersonal = "Calle Mayor 1", numeroSeguridadSocial = "123456789012")
        val id = runBlocking { db.homeDao().addEmployee(original) }
        try {
            compose.setContent { HomeModuleTheme { HomeEntryForm("employee-edit", vm, HomeWorkspaceState(loading = false), { saved.set(true) }, original.copy(id = id)) } }
            compose.onNodeWithTag("employee-phone-country").performScrollTo().assertTextContains("🇬🇧 +44", substring = true)
            compose.onNodeWithTag("employee-phone-number").performScrollTo().performTextReplacement("123")
            save()
            compose.onNodeWithTag("employee-phone-number").assertIsFocused()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            assertFalse(saved.get())
            assertEquals("+442079460018", runBlocking { db.homeDao().employees().first().single().telefono })
            compose.onNodeWithTag("employee-phone-number").performTextReplacement("+44 20 7946 0018")
            save(); compose.waitUntil(10_000) { saved.get() }
            val updated = runBlocking { db.homeDao().employees().first().single() }
            assertEquals(id, updated.id)
            assertEquals("+442079460018", updated.telefono)
        } finally { vm.viewModelScope.cancel(); db.close() }
    }
}
