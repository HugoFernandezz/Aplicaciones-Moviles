package com.example.gestorempresa.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.*
import com.example.gestorempresa.data.profile.*
import com.example.gestorempresa.data.settings.RoomSettingsRepository
import com.example.gestorempresa.domain.auth.*
import com.example.gestorempresa.domain.profile.*
import com.example.gestorempresa.ui.auth.AuthViewModel
import com.example.gestorempresa.ui.navigation.*
import com.example.gestorempresa.ui.profile.*
import com.example.gestorempresa.ui.settings.SettingsViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

private class AuthFixture : AuthRepository {
    override val sesion = MutableStateFlow<SesionUsuario?>(null)
    override suspend fun registrar(datos: RegistroUsuario) = ResultadoAuth.Correcto
    override suspend fun iniciarSesion(email: String, password: String) = ResultadoAuth.Correcto
    override suspend fun recuperarPassword(email: String) = ResultadoAuth.Correcto
    override suspend fun cerrarSesion(): ResultadoAuth { sesion.value = null; return ResultadoAuth.Correcto }
}

private class ProfileFixture(val context: Context) {
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val auth = AuthFixture()
    val photos = FotoPerfilStorage(context)
    private val cloud = object : PerfilCuentaService {
        override suspend fun obtener(uid: String): PerfilCuentaRemoto? = null
        override suspend fun crearSiFalta(perfil: PerfilCuentaRemoto) = perfil
        override suspend fun actualizar(perfil: PerfilCuentaRemoto) { }
    }
    val repo = RoomProfileRepository(db, photos, cloud)
    private val session1: SesionUsuario
    private val session2: SesionUsuario
    init {
        fun seed(name: String, email: String): SesionUsuario = runBlocking {
            val company = db.authDao().insertarEmpresa(EmpresaEntity(nombre = "Empresa $name", sector = "Limpieza", creadaEn = 0))
            val id = db.authDao().insertarUsuario(UsuarioEntity(nombre = name, apellidos = "Pérez", email = email,
                empresaId = company, passwordHash = "", passwordSalt = "", passwordIterations = 0))
            db.ajustesDao().guardarAjustes(AjustesEntity(id, avisosVencimientos = false, diasAntelacion = 15, avisosTareas = false, tema = "CLARO"))
            SesionUsuario(Usuario(id, name, "Pérez", email, company), Empresa(company, "Empresa $name", "Limpieza"), Long.MAX_VALUE)
        }
        session1 = seed("Ana", "ana@example.invalid")
        session2 = seed("Otra", "otra@example.invalid")
        auth.sesion.value = session1
    }
    val profile = ProfileViewModel(auth, repo)
    val settings = SettingsViewModel(auth, RoomSettingsRepository(db))
    val authVm = AuthViewModel(auth, SavedStateHandle())
    val user get() = session1.usuario.id
    fun otherUser() { auth.sesion.value = session2 }
    fun close() {
        profile.viewModelScope.cancel(); settings.viewModelScope.cancel(); authVm.viewModelScope.cancel(); db.close()
    }
}

/** Simula únicamente las aplicaciones externas; cámara/galería reales se lanzan con los contratos compartidos. */
private class PhotoRegistry(private val context: Context, private val image: File) : ActivityResultRegistry() {
    var cameraCalls = 0
    var galleryCalls = 0
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        when (contract) {
            is ActivityResultContracts.TakePicture -> {
                cameraCalls++
                context.contentResolver.openOutputStream(input as Uri)!!.use { output -> image.inputStream().use { it.copyTo(output) } }
                dispatchResult(requestCode, Activity.RESULT_OK, null)
            }
            is ActivityResultContracts.PickVisualMedia -> {
                galleryCalls++
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(image)))
            }
            else -> error("Contrato inesperado: ${contract.javaClass.simpleName}")
        }
    }
}

class ProfileFlowsUiTest {
    @Test fun decodificadorCompartidoRespetaGiroYEspejoExif() {
        val source = image()
        val jpeg = File(context.cacheDir, "exif_${UUID.randomUUID()}.jpg")
        try {
            jpeg.outputStream().use { BitmapFactory.decodeFile(source.path).compress(Bitmap.CompressFormat.JPEG, 100, it) }
            android.media.ExifInterface(jpeg.path).apply {
                setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6"); saveAttributes()
            }
            val rotated = com.example.gestorempresa.data.media.decodePhotoBitmap(jpeg.path)!!
            assertEquals(300, rotated.width); assertEquals(900, rotated.height)
            android.media.ExifInterface(jpeg.path).apply {
                setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "2"); saveAttributes()
            }
            val mirrored = com.example.gestorempresa.data.media.decodePhotoBitmap(jpeg.path)!!
            val left = mirrored.getPixel(50, 150)
            assertTrue(Color.blue(left) > Color.green(left) + 50)
        } finally { source.delete(); jpeg.delete() }
    }
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun image(): File {
        val bitmap = Bitmap.createBitmap(900, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint()
        paint.color = Color.GREEN; canvas.drawRect(0f, 0f, 300f, 300f, paint)
        paint.color = Color.RED; canvas.drawRect(300f, 0f, 600f, 300f, paint)
        paint.color = Color.BLUE; canvas.drawRect(600f, 0f, 900f, 300f, paint)
        return File(context.cacheDir, "fixture_${UUID.randomUUID()}.png").also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun showProfile(fixture: ProfileFixture, registry: PhotoRegistry) {
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            MaterialTheme { ProfileScreen(fixture.profile) }
        } }
        compose.waitUntil(10_000) { fixture.profile.ui.value.perfil != null }
    }
    private fun waitCrop() = compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo-crop-area").fetchSemanticsNodes().isNotEmpty() }

    @Test fun lapizAbreCamaraYGaleriaYCancelarNoSustituyeLaFoto() {
        val fixture = ProfileFixture(context)
        val source = image()
        runBlocking { fixture.repo.cambiarFoto(fixture.user, Uri.fromFile(source).toString()) }
        val old = runBlocking { fixture.db.perfilDao().buscarPerfil(fixture.user)!!.fotoRuta!! }
        val registry = PhotoRegistry(context, source)
        try {
            showProfile(fixture, registry)
            compose.onNodeWithTag("profile-photo-edit").performScrollTo().performClick()
            compose.onNodeWithText("Cámara").assertIsDisplayed()
            compose.onNodeWithText("Galería").performClick()
            waitCrop()
            assertEquals(1, registry.galleryCalls)
            assertEquals(old, runBlocking { fixture.db.perfilDao().buscarPerfil(fixture.user)?.fotoRuta })
            compose.onNodeWithTag("photo-cancel").performClick()
            compose.onNodeWithText("Ajustar fotografía").assertDoesNotExist()
            assertEquals(old, runBlocking { fixture.db.perfilDao().buscarPerfil(fixture.user)?.fotoRuta })
            assertTrue(File(old).isFile)
        } finally { fixture.close(); source.delete() }
    }

    @Test fun camaraPasaPorAjusteYMoverAmpliarConfirmarGuardaLaZonaElegida() {
        val fixture = ProfileFixture(context)
        val source = image()
        val registry = PhotoRegistry(context, source)
        try {
            showProfile(fixture, registry)
            compose.onNodeWithTag("profile-photo-edit").performScrollTo().performClick()
            compose.onNodeWithText("Cámara").performClick()
            waitCrop()
            assertEquals(1, registry.cameraCalls)
            assertNull(runBlocking { fixture.db.perfilDao().buscarPerfil(fixture.user)?.fotoRuta })
            compose.onNodeWithTag("photo-crop-area").performTouchInput {
                swipe(Offset(width * .9f, height * .5f), Offset(width * .1f, height * .5f), 500)
            }
            compose.onNodeWithTag("photo-zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithTag("photo-confirm").performClick()
            compose.waitUntil(10_000) { fixture.profile.ui.value.perfil?.fotoRuta != null && !fixture.profile.ui.value.guardando }
            val path = fixture.profile.ui.value.perfil!!.fotoRuta!!
            assertTrue(fixture.photos.pertenece(fixture.user, path))
            val bitmap = BitmapFactory.decodeFile(path)
            assertEquals(768, bitmap.width); assertEquals(768, bitmap.height)
            val center = bitmap.getPixel(384, 384)
            assertTrue("El recorte debe mostrar la franja azul, no el centro rojo original", Color.blue(center) > Color.red(center) + 50)
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Foto de perfil").fetchSemanticsNodes().isNotEmpty() }
        } finally { fixture.close(); source.delete() }
    }

    @Test fun editarDireccionConservaPrefijoYValidaConFocoSinPerderOtrosCampos() {
        val fixture = ProfileFixture(context)
        val source = image()
        runBlocking { fixture.repo.guardarPerfil(fixture.user, fixture.auth.sesion.value!!.empresa.id,
            DatosPerfil("Ana", "Pérez", "02079460018", "", "Gerente", "Empresa Ana", "Limpieza", telefonoRegion = "GB")) }
        try {
            showProfile(fixture, PhotoRegistry(context, source))
            compose.onNodeWithText("Editar datos").performScrollTo().performClick()
            compose.onNodeWithTag("profile-phone-country").performScrollTo().assertTextContains("+44", substring = true)
            compose.onNodeWithText("Nombre *").performScrollTo().performTextReplacement("")
            compose.onNodeWithTag("profile-save").performScrollTo().performClick()
            compose.onNodeWithText("Nombre *").assertIsFocused().assertIsDisplayed()
            compose.onNodeWithText("Apellidos *").assertTextContains("Pérez")
            compose.onNodeWithText("Nombre *").performTextReplacement("Andrea")
            compose.onNodeWithText("Dirección (opcional)").performScrollTo().performTextReplacement("Calle Mayor 10")
            compose.onNodeWithTag("profile-save").performScrollTo().performClick()
            compose.waitUntil(10_000) { !fixture.profile.ui.value.editando && !fixture.profile.ui.value.guardando }
            val row = runBlocking { fixture.db.perfilDao().buscarPerfil(fixture.user)!! }
            assertEquals("+442079460018", row.telefono)
            assertEquals("GB", row.telefonoRegion)
            assertEquals("Calle Mayor 10", row.direccion)
            compose.onNodeWithText("Perfil actualizado.").assertIsDisplayed()
            // Espera a que termine la confirmación, que ocupa el borde inferior sobre el botón.
            compose.waitUntil(10_000) { fixture.profile.ui.value.mensaje == null }
            compose.onNodeWithText("Editar datos").performScrollTo().performClick()
            compose.waitUntil(5_000) { fixture.profile.ui.value.editando }
            compose.onNodeWithTag("profile-phone-country").performScrollTo().assertTextContains("+44", substring = true)
        } finally { fixture.close(); source.delete() }
    }

    @Test fun menuActualizaLaFotoYNoMuestraLaDeOtraCuenta() {
        val fixture = ProfileFixture(context)
        val source = image()
        try {
            compose.setContent {
                val state by fixture.profile.ui.collectAsState()
                val p = state.perfil
                MaterialTheme { MenuLateral("Empresa", p?.nombreCompleto.orEmpty(), emptyList(), "", {}, p?.fotoRuta, p?.iniciales.orEmpty()) }
            }
            runBlocking { fixture.repo.cambiarFoto(fixture.user, Uri.fromFile(source).toString()) }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Foto de perfil").fetchSemanticsNodes().isNotEmpty() }
            fixture.otherUser()
            compose.waitUntil(10_000) { fixture.profile.ui.value.perfil?.nombre == "Otra" }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Foto de perfil").assertDoesNotExist()
            compose.onNodeWithTag("menu-profile-avatar").assertExists()
        } finally { fixture.close(); source.delete() }
    }

    @Test fun menuAbrePerfilEdicionSeguridadNotificacionesYCierraLaSesion() {
        val fixture = ProfileFixture(context)
        try {
            compose.setContent { MaterialTheme { AuthRoot(fixture.authVm, contenidoAutenticado = { s, logout ->
                MainScaffold(s, logout, fixture.profile, fixture.settings)
            }) } }
            fun open(label: String) {
                compose.onNodeWithTag("main-INICIO").performClick()
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("menu-lateral"))).performScrollTo().performClick()
                compose.waitForIdle()
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("main-INICIO").fetchSemanticsNodes().isNotEmpty() }
            open("Perfil"); compose.onNodeWithText("Mi perfil").assertExists()
            open("Editar datos"); compose.onNodeWithText("Nombre *").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cancelar").performScrollTo().performClick()
            open("Ajustes"); compose.onNodeWithText("Preferencias").assertExists()
            open("Seguridad"); compose.onNodeWithText("Seguridad").assertIsDisplayed()
            compose.onNodeWithText("Se mantiene hasta cerrar sesión o hasta que el servicio la invalide.").assertExists()
            open("Notificaciones"); compose.onNodeWithText("Avisos de vencimientos").assertIsDisplayed()
            open("Cerrar sesión")
            compose.onNode(hasText("Cerrar sesión") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { fixture.auth.sesion.value == null }
            compose.onNodeWithText("Bienvenido de nuevo").assertExists()
            compose.onNodeWithTag("main-PERFIL").assertDoesNotExist()
        } finally { fixture.close() }
    }
}
