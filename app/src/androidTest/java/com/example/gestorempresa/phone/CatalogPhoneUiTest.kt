package com.example.gestorempresa.phone

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gestorempresa.data.local.HomeDatabase
import com.example.gestorempresa.data.local.entities.*
import com.example.gestorempresa.ui.home.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CatalogPhoneUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fill(label: String, value: String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun country(prefix: String, query: String, region: String) {
        compose.onNodeWithTag("$prefix-country").performScrollTo().performClick()
        compose.onNodeWithTag("phone-country-search").performTextInput(query)
        compose.onNodeWithTag("phone-country-$region").performClick()
    }

    @Test fun altaSubcontratadoAdmiteOchoDigitosConPrefijoYConservaElBorrador() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent { HomeModuleTheme { ModuleRecordForm("subcontractor-new", HomeWorkspaceState(loading = false), vm, onBack = { saved.set(true) }) } }
            compose.onNodeWithTag("subcontractor-phone-country").performScrollTo().assertTextContains("🇪🇸 +34", substring = true)
            fill("Nombre comercial / empresa *", "Subcontrata danesa"); fill("Oficio *", "Pintura")
            country("subcontractor-phone", "Dinamarca", "DK")
            compose.onNodeWithTag("subcontractor-phone-number").performScrollTo().performTextReplacement("123")
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.onNodeWithTag("subcontractor-phone-number").assertIsFocused()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            assertFalse(saved.get())
            compose.onNodeWithTag("subcontractor-phone-number").performTextReplacement("32123456")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("subcontractor-phone-country").performScrollTo().assertTextContains("🇩🇰 +45", substring = true)
            compose.onNodeWithTag("subcontractor-phone-number").assertTextContains("32123456")
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.waitUntil(10_000) { saved.get() }
            assertEquals("+4532123456", runBlocking { db.homeDao().subcontractors().first().single().telefono })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }

    @Test fun altaProveedorMuestraEspanaYGuardaTelefonoInternacional() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        try {
            compose.setContent { HomeModuleTheme { ModuleRecordForm("supplier-new", HomeWorkspaceState(loading = false), vm, onBack = { saved.set(true) }) } }
            compose.onNodeWithTag("supplier-phone-country").performScrollTo().assertTextContains("🇪🇸 +34", substring = true)
            fill("Nombre comercial / empresa *", "Proveedor británico")
            country("supplier-phone", "Reino Unido", "GB")
            compose.onNodeWithTag("supplier-phone-number").performScrollTo().performTextReplacement("020 7946 0018")
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.waitUntil(10_000) { saved.get() }
            assertEquals("+442079460018", runBlocking { db.homeDao().suppliers().first().single().telefono })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }

    @Test fun ambasAltasConservanElTelefonoComoCampoOpcional() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val route = mutableStateOf("subcontractor-new")
        val saves = AtomicInteger(0)
        try {
            compose.setContent { HomeModuleTheme { ModuleRecordForm(route.value, HomeWorkspaceState(loading = false), vm, onBack = { saves.incrementAndGet() }) } }
            fill("Nombre comercial / empresa *", "Subcontrata sin teléfono"); fill("Oficio *", "Limpieza")
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.waitUntil(10_000) { saves.get() == 1 }
            compose.runOnIdle { route.value = "supplier-new" }
            fill("Nombre comercial / empresa *", "Proveedor sin teléfono")
            compose.onNodeWithText("Guardar").performScrollTo().performClick()
            compose.waitUntil(10_000) { saves.get() == 2 }
            assertEquals("", runBlocking { db.homeDao().subcontractors().first().single().telefono })
            assertEquals("", runBlocking { db.homeDao().suppliers().first().single().telefono })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }

    private val subcontractor = SubcontratadoEntity(1, "Subcontrata danesa", "Contacto original", "", "+4532123456", "", "Pintura", 0.0)
    private val supplier = ProveedorEntity(1, "Proveedor británico", "", "+442079460018", "", "Contacto original")
    private val project = UnidadTrabajoEntity(1, "Proyecto con el mismo ID", "PROYECTO", "", "Murcia", fechaInicio = "2026-01-01")
    private fun state() = HomeWorkspaceState(loading = false, subcontractors = listOf(subcontractor), suppliers = listOf(supplier), projects = listOf(project))

    @Test fun editarProveedorConservaPrefijoYNoCargaDatosDeOtrasTablasConElMismoId() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        runBlocking { db.homeDao().addSubcontractor(subcontractor); db.homeDao().addSupplier(supplier); db.homeDao().addProject(project) }
        try {
            compose.setContent { HomeModuleTheme { EditCatalogItemScreen(CatalogSelection(CatalogKind.SUPPLIERS, 1), state(), vm) { saved.set(true) } } }
            compose.onNodeWithText("Nombre de empresa *").assertTextContains("Proveedor británico")
            compose.onNodeWithTag("supplier-phone-country").performScrollTo().assertTextContains("🇬🇧 +44", substring = true)
            fill("Persona de contacto", "Contacto actualizado")
            compose.onNodeWithText("Guardar cambios").performScrollTo().performClick()
            compose.waitUntil(10_000) { saved.get() }
            val updated = runBlocking { db.homeDao().suppliers().first().single() }
            assertEquals("+442079460018", updated.telefono); assertEquals("Proveedor británico", updated.nombreEmpresa)
            assertEquals("Contacto actualizado", updated.personaContacto)
            assertEquals(subcontractor, runBlocking { db.homeDao().subcontractors().first().single() })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }

    @Test fun editarSubcontratadoBloqueaTelefonoInvalidoYConservaOtrosValores() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeDatabase::class.java).build()
        val vm = HomeWorkspaceViewModel(db.homeDao())
        val saved = AtomicBoolean(false)
        runBlocking { db.homeDao().addSubcontractor(subcontractor); db.homeDao().addSupplier(supplier); db.homeDao().addProject(project) }
        try {
            compose.setContent { HomeModuleTheme { EditCatalogItemScreen(CatalogSelection(CatalogKind.SUBCONTRACTORS, 1), state(), vm) { saved.set(true) } } }
            compose.onNodeWithText("Nombre comercial *").assertTextContains("Subcontrata danesa")
            compose.onNodeWithTag("subcontractor-phone-country").performScrollTo().assertTextContains("🇩🇰 +45", substring = true)
            fill("Persona de contacto", "Contacto actualizado")
            compose.onNodeWithTag("subcontractor-phone-number").performScrollTo().performTextReplacement("123")
            compose.onNodeWithText("Guardar cambios").performScrollTo().performClick()
            compose.onNodeWithTag("subcontractor-phone-number").assertIsFocused()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            assertFalse(saved.get())
            assertEquals(subcontractor, runBlocking { db.homeDao().subcontractors().first().single() })
            compose.onNodeWithTag("subcontractor-phone-number").performTextReplacement("+45 32 12 34 56")
            compose.onNodeWithText("Guardar cambios").performScrollTo().performClick()
            compose.waitUntil(10_000) { saved.get() }
            val updated = runBlocking { db.homeDao().subcontractors().first().single() }
            assertEquals("+4532123456", updated.telefono); assertEquals("Contacto actualizado", updated.personaContacto)
            assertEquals(supplier, runBlocking { db.homeDao().suppliers().first().single() })
        } finally { vm.viewModelScope.cancel(); db.close() }
    }
}
