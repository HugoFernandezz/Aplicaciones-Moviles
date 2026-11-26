# Perfil y Ajustes — revisión del miembro 4

Trabajo sobre `codex/auth-multidispositivo`, en el worktree `auth-multidispositivo`, conservando la autenticación y los teléfonos de empleados implementados anteriormente.

## Errores detectados y corregidos

| Causa comprobada | Corrección |
| --- | --- |
| El lápiz era una `Surface` con un icono, sin acción de clic. | Botón propio con área táctil de 48 dp y descripción accesible. Abre directamente Cámara/Galería. El avatar también puede abrir el mismo selector. |
| Perfil lanzaba solamente galería y guardaba la imagen inmediatamente al recibir la URI. | Reutiliza `rememberPhotoPicker` del módulo compartido. Ambas fuentes pasan al ajuste antes de cambiar la foto definitiva. Se retira el botón independiente Añadir/Cambiar foto; se conserva Quitar foto. |
| No existía ajuste compartido en el repositorio consultado. La usuaria aclaró que había que implementarlo. | Un único `PhotoAdjustmentScreen` en `ui/advanced/media`: arrastre, pellizco, deslizador de zoom, encuadre circular, restablecer, confirmar y cancelar. No se duplica la cámara ni la galería de Hugo. |
| Se copiaban bytes arbitrarios a un archivo llamado `.jpg`; Perfil no interpretaba EXIF. | Validación y decodificación compartida, límite de memoria, orientación EXIF y salida JPEG. Los originales y recortes intermedios son temporales. La fila del perfil solo cambia después de confirmar y completar el guardado. |
| La carga asíncrona del avatar podía conservar momentáneamente una imagen anterior al cambiar de ruta. | La carga se reinicia por ruta, las consultas pertenecen al usuario activo y se rechazan rutas de otro usuario. |
| El menú no mostraba la fotografía y no tenía accesos al perfil ni a las secciones de Ajustes. | Avatar real en cabecera y navegación inferior, iniciales por defecto y rutas funcionales Perfil, Editar datos, Ajustes, Seguridad, Notificaciones y Cerrar sesión. |
| El formulario tenía un solo texto de teléfono; faltaba dirección y no se almacenaba la selección del país. | Prefijo y número separados visualmente. Se reutiliza el selector internacional existente. Se guardan E.164 y país por separado, incluso si el teléfono opcional queda vacío. Dirección se añade al modelo, a Room y al perfil remoto. |
| Editar un campo borraba los demás errores; no había desplazamiento al primer error. | Campos obligatorios marcados, errores rojos por campo, sector rojo, foco y desplazamiento en orden visual, conservación de valores y mensajes de confirmación. |
| Cambiar de cuenta solo sustituía `perfil`, dejando formulario, errores y operaciones de la cuenta anterior. | Reinicio de estado por identidad, cancelación de operaciones de perfil y protección de resultados por generación de sesión. Una selección de foto de una cuenta anterior no se asigna a otra. |
| Ajustes guardaba instantáneas completas en corutinas concurrentes y mostraba cambios aunque fallase su escritura. | Mutaciones ordenadas con exclusión mutua y lectura del valor persistido. El éxito refleja un guardado confirmado; un fallo conserva el valor anterior e informa al usuario. |
| La fecha de sesión intentaba representar `Long.MAX_VALUE`, usado por las sesiones renovables de Firebase. | Texto de sesión persistente, sin fecha ficticia. La recuperación de contraseña informa de solicitud aceptada, sin asegurar entrega del correo. |
| Se programaban avisos antes de cargar las preferencias; la revisión manual ignoraba el interruptor de vencimientos y al salir solo se cancelaba el trabajo periódico. | Se espera a las preferencias del usuario activo, se respetan ambos interruptores y se cancelan también los trabajos manuales etiquetados. El permiso se comprueba con Android y hay acceso real a sus ajustes de notificaciones. |

## Persistencia, identidad e imágenes

`MIGRATION_6_7` añade `direccion` y `telefonoRegion` a `perfiles`. Conserva los identificadores, el teléfono, el resto de datos, la ruta de foto y las preferencias. Para registros antiguos sin país explícito, se deduce desde el teléfono internacional o se muestra España como selección inicial.

La foto definitiva se guarda en `files/fotos_perfil/usuario_<id>/` con un nombre nuevo por imagen. La ruta se conserva en Room y se observa desde Perfil y el menú. Se mantiene compatibilidad con los nombres antiguos `usuario_<id>_...`. Una consulta no expone una ruta que pertenece a otra cuenta, y sustituir o eliminar una foto solo borra la imagen anterior si pertenece al usuario correspondiente. Un fallo al guardar la fila limpia la imagen nueva sin borrar la anterior.

La selección y el ajuste usan archivos privados en caché. Cancelar conserva la foto previa. Confirmar produce un cuadrado de 768 × 768 que se muestra como avatar circular. El encuadre se guarda en coordenadas relativas para conservarlo durante cambios de tamaño. El rango de ampliación es de 1 a 6, sin dejar áreas vacías dentro del recorte.

Los datos de nombre, apellidos, empresa, sector, teléfono, país, dirección, DNI/NIE y cargo siguen la integración de autenticación existente por UID. `direccion` y `telefonoRegion` son campos opcionales nuevos en Firestore para aceptar documentos antiguos. Las reglas siguen exigiendo la identidad propietaria y no permiten modificar roles ni credenciales.

Fotos y preferencias son locales por usuario en cada instalación. No se ha añadido sincronización de fotografías ni preferencias entre teléfonos. La persistencia al reabrir la misma instalación usa archivos privados y Room.

## Código compartido e integración

- Cámara y galería: `ui/advanced/media/PhotoComponents.kt`, API `rememberPhotoPicker`, ya utilizada por los módulos de Hugo.
- Ajuste único: `PhotoAdjustmentScreen.kt`, `PhotoAdjustmentStore.kt` y geometría `data/media/CropFrame.kt`.
- Decodificación compartida: `data/media/ImageDecoding.kt`. La función pública anterior `decodeImage` delega en ella para conservar los usos existentes.
- Teléfono: `InternationalPhoneField` acepta modo opcional y presentación en fila, manteniendo el comportamiento y las etiquetas de prueba del formulario de empleados.
- Cierre de sesión: continúa en `AuthViewModel`/`AuthRepository`; se elimina la navegación privada y se cancelan los avisos de esta instalación.
- Los listados, documentos y fotos de empleados/proyectos mantienen sus flujos. El ajuste compartido queda disponible para que Hugo lo reutilice allí si el equipo lo solicita.

## Verificación

| Comprobación | Resultado |
| --- | --- |
| Suite unitaria completa | **128/128 aprobadas**, 0 fallos y 0 omitidas. Incluye el test de Inicio corregido anteriormente. |
| Compilación normal | `assembleDebug` termina correctamente. APK conservado en `build/entrega/gestor-empresa-debug.apk`. |
| Análisis estático | `lintDebug` termina correctamente: **0 errores y 55 advertencias**. Informe en `build/entrega/lint-results-debug.html`. No se han actualizado masivamente las dependencias del equipo. |
| Android: Perfil y teléfonos de empleados | **9/9 aprobadas** en API 36: 6 de Perfil y 3 de regresión de empleados. Registro en `build/entrega/profile-test-results.log`. |
| Reglas de Firestore | **6/6 aprobadas** con `demo-gestor-empresa`: compatibilidad con documentos antiguos, propietario frente a otras cuentas/anónimo y nuevos campos de dirección/país. No despliega reglas al proyecto real. |

El informe unitario completo está en `build/entrega/unit-tests/index.html`. Las pruebas añadidas cubren geometría de recorte, migración 6 → 7, restauración de datos y fotografía, país opcional, dirección, aislamiento por usuario, guardados rápidos, errores y cambio de sesión.

En Android se verifican: lápiz → Cámara/Galería; selección temporal sin sobrescribir la imagen anterior; cancelación; arrastre y zoom con comprobación del color de los píxeles del recorte guardado; JPEG de 768 × 768; giro y espejo EXIF; foto del menú reactiva y aislada por cuenta; validación con foco y desplazamiento; confirmación visual; conservación de teléfono +44 y país al editar dirección; todos los accesos del menú y desaparición de las pantallas privadas al cerrar sesión. La primera ejecución detectó una espera ausente en la prueba al pulsar Editar durante el mensaje de confirmación; se añadió la espera y la ejecución conjunta final pasa completa.

La suite Android de este requisito simula las aplicaciones externas mediante `ActivityResultRegistry`, usando los contratos reales compartidos `TakePicture` y `PickVisualMedia`. La preparación de archivos, el ajuste, los píxeles recortados, la compresión, la escritura en Room, el avatar, el formulario y la navegación se ejecutan realmente en Android. Esta prueba no certifica el funcionamiento físico de la cámara ni la interfaz de cada galería instalada.

Comandos principales:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
.\gradlew.bat assembleDebug assembleDebugAndroidTest '-PhomeUiTests=true' '-PfocusedAndroidTests=profile,phone' -I tools/auth-tests.init.gradle
adb -s emulator-5580 shell am instrument -w -r -e class com.example.gestorempresa.profile.ProfileFlowsUiTest,com.example.gestorempresa.phone.EmployeePhoneUiTest com.example.gestorempresa.uitests.test/androidx.test.runner.AndroidJUnitRunner
.\tools\firebase-tests\node_modules\.bin\firebase.cmd emulators:exec --project demo-gestor-empresa --only firestore "node tools/firebase-tests/rules.test.mjs"
```

En este equipo se utiliza JDK 17, el almacén de certificados de Windows y el init script local de Robolectric, como se explica en la revisión de autenticación. La salida se dirige a `android-profile-build` fuera de OneDrive. Los APK de interfaz usan `com.example.gestorempresa.uitests`; el emulador se inicia en modo de solo lectura. El APK de entrega normal se conserva aparte en `build/entrega/gestor-empresa-debug.apk`.

El init script limita las fuentes de tests Android porque `HomeModuleUiTest` de `main` mantiene llamadas a firmas antiguas de otros módulos. No modifica esas pantallas ni declara aprobada la suite instrumentada completa de Inicio.

## Pendiente de comprobación e integración externa

1. Probar cámara y galería reales en teléfonos físicos de distintos fabricantes, con fotos de alta resolución, orientación EXIF, cancelación y rotación de pantalla.
2. Revisar visualmente tamaños de letra grandes, TalkBack y pantallas pequeñas en el ajuste y en el formulario.
3. Reglas publicadas el 26/11/2026 con autorización expresa en `gestor-empresa-74d8b`, tras crear la base `(default)` en Madrid. Otras colecciones siguen cerradas; cualquier futura integración requiere sus propias reglas. Detalles en [INCIDENCIA_ACCESO.md](INCIDENCIA_ACCESO.md).
4. Verificar con el proyecto real la sincronización de dirección y país entre instalaciones y la recepción real del correo de recuperación. Los emuladores no envían emails reales.
5. La sincronización de fotografías y preferencias entre dispositivos queda fuera de la arquitectura actual; requeriría acordar almacenamiento remoto con el equipo.

## Archivos del requisito 4

- `ui/profile/ProfileScreen.kt`, `ProfileViewModel.kt`, `components/ProfileAvatar.kt`, `components/ProfileEditForm.kt`.
- `domain/profile/PerfilModels.kt`, `PerfilValidator.kt`.
- `data/profile/FotoPerfilStorage.kt`, `RoomProfileRepository.kt`.
- `data/local/AppDatabase.kt`, `ProfileMigration.kt`, `entities/PerfilEntity.kt`, `dao/PerfilDao.kt` y esquema 7 exportado.
- `ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `data/settings/RoomSettingsRepository.kt`.
- `ui/navigation/MainScaffold.kt`, `MenuLateral.kt`, `ui/home/HomeListsContent.kt`, `InternationalPhoneField.kt`.
- `ui/advanced/media/PhotoComponents.kt`, `PhotoAdjustmentScreen.kt`, `PhotoAdjustmentStore.kt`.
- `data/media/ImageDecoding.kt`, `CropFrame.kt`, `ui/advanced/dates/DueDateNotifications.kt`.
- `domain/auth/PerfilCuentaService.kt`, `data/auth/FirestorePerfilCuentaService.kt`, `FirebaseAuthRepository.kt`, `firestore.rules`.
- Tests: `PerfilValidatorTest`, `CropFrameTest`, `ProfileViewModelTest`, `SettingsViewModelTest`, `ProfileRepositoryTest`, `DatabaseMigrationTest`, `MultidispositivoTest`, `ProfileFlowsUiTest` y `tools/firebase-tests/rules.test.mjs`.
- `tools/auth-tests.init.gradle` y este informe. Rutas Kotlin relativas a `app/src/main/java/com/example/gestorempresa`; tests en sus carpetas correspondientes.

Referencias: [selector oficial de fotos](https://developer.android.com/training/data-storage/shared/photo-picker), [gestos multitáctiles de Compose](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch), [reglas de Firestore](https://firebase.google.com/docs/firestore/security/rules-conditions).
