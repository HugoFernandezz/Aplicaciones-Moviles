# Revisión de autenticación — 26/11/2026

Base: `origin/main`, commit `93ea889`. Rama: `codex/auth-multidispositivo`.
El trabajo está en la carpeta `auth-multidispositivo`, un worktree independiente. No se han sustituido las dos copias locales anteriores ni sus cambios de Android Studio.

Actualización del requisito 4: la revisión y los resultados posteriores de Perfil y Ajustes están en [REVISION_PERFIL_AJUSTES.md](REVISION_PERFIL_AJUSTES.md), cuya suite unitaria alcanzó 128/128. Las comprobaciones posteriores de DNI y teléfonos de proveedores y subcontratados están en [REVISION_DNI_CONTACTOS.md](REVISION_DNI_CONTACTOS.md). Los resultados de 107 pruebas de este informe corresponden a la entrega anterior de autenticación y teléfonos de empleados.

Incidencia posterior de acceso real: Firestore ya está activado y sus reglas publicadas. Se corrigió el certificado del entorno Debug local y la usuaria confirmó la entrada en su cuenta. Detalles y pruebas en [INCIDENCIA_ACCESO.md](INCIDENCIA_ACCESO.md).

## Causas comprobadas en el código

| Hallazgo | Clasificación | Corrección |
| --- | --- | --- |
| `FirebaseAuthRepository.iniciarSesion` autenticaba en Firebase, pero devolvía `PERFIL_NO_DISPONIBLE` si no había usuario en Room. El alta solo guardaba empresa y perfil en ese teléfono. | Error que explica el acceso fallido desde otra instalación. | Perfil remoto en `authProfiles/{firebaseUid}` y recuperación transaccional de su copia local tras autenticar. |
| La sesión remota dependía además de una caducidad local fija de siete días. | Error de persistencia: podía exigir contraseña aunque Firebase mantuviese una sesión válida. | La caducidad local se mantiene para cuentas locales antiguas; las vinculadas dependen del SDK y su identidad. Comprobación de revocación al suscribirse y cada minuto mientras se observa la sesión. |
| `actualizarSesion` cerraba sesión ante cualquier excepción no clasificada. | Error: un fallo temporal del servicio podía expulsar al usuario. | Solo los errores de usuario/token inválido cierran la sesión. La falta de red no se presenta como contraseña incorrecta. |
| Editar un campo de registro borraba todos los errores. Faltaban foco/desplazamiento al primer error y borde rojo del selector de sector. Confirmación vacía podía pasar sin error propio cuando ambas contraseñas estaban vacías. | Errores de formulario. | Revalidación conservando datos, mensajes por campo, contador de intentos, foco y `bringIntoView` en orden visual, sector rojo y confirmación obligatoria. |
| Los nombres y datos de empresa editados desde Perfil solo se escribían en Room. | Error de integración con recuperación en otro dispositivo. | Guardado compartido en su repositorio antes de confirmar éxito local. Pantallas de Perfil y Ajustes sin cambios. |

No se ha encontrado uso de identificadores de teléfono ni una política de sesión única en el código. En la revisión inicial todavía no se había auditado la consola del proyecto: no se atribuyó entonces el fallo a una configuración remota no comprobada. La incidencia posterior sí confirmó y corrigió Firestore desactivado y el certificado de desarrollo. Firebase Authentication ya era el proveedor y se conserva. Firestore añade almacenamiento compartido de perfil, no sustituye la autenticación.

## Identidad e integración con el miembro 4

- `SesionUsuario.usuario.firebaseUid`: identidad estable de la cuenta entre instalaciones.
- `SesionUsuario.empresa.remoteId`: identidad compartida de la empresa. En el modelo actual, cada alta crea un administrador y su propia empresa; se usa su UID como ID de empresa. No se ha inventado un sistema de invitaciones o empresas compartidas por varias cuentas.
- `usuario.id` y `empresa.id`: claves locales `Long`. Se conservan al migrar; pueden ser distintas entre teléfonos. Inicio, Perfil, Ajustes y las bases de datos locales siguen usando sus claves actuales.
- `RoomProfileRepository` publica nombre, apellidos, empresa, sector, teléfono, DNI/NIE y cargo en el mismo documento. No modifica correo, contraseña ni roles. Un fallo remoto impide anunciar un guardado exitoso. Foto y ajustes siguen siendo locales.
- Al iniciar sesión se descarga el perfil del servidor y se actualizan las filas existentes sin duplicar usuarios/empresas ni borrar foto o ajustes. El perfil existente en el servidor tiene prioridad sobre copias antiguas.
- Los cambios se recuperan en el otro teléfono al iniciar sesión de nuevo. La edición simultánea usa el último guardado confirmado; no se ha añadido actualización visual en tiempo real ni un editor con resolución de conflictos.

Este documento deja el contrato de integración para el miembro 4; no se ha enviado ningún mensaje externo al equipo.

## Alta, migración y recuperación

Las cuentas nuevas publican el perfil durante el registro. La autenticación se hace siempre con Firebase para cuentas vinculadas; no se guardan contraseñas, hashes o tokens nuevos en Room. Los hashes antiguos se borran al vincular. No se crean cuentas durante el acceso normal desde un teléfono nuevo.

Una cuenta antigua necesita entrar una vez, con esta versión, desde la instalación que aún conserva el perfil. Tras verificar Firebase y, cuando corresponda, el hash de la cuenta local antigua, se publica el documento solo si falta. La creación transaccional evita que dos copias antiguas sobrescriban un perfil ya publicado. Si ya no existe ninguna copia de datos y nunca se publicó el perfil, no se puede reconstruir automáticamente su empresa: requiere recuperación administrativa.

Authentication y Firestore no forman una única transacción. Si falla la publicación durante el alta se conserva una copia vinculada local y se pide iniciar sesión en ese teléfono para completarla, sin registrarse otra vez. No se elimina una cuenta remota por un timeout ambiguo. Si el proceso muere justo después del alta de Authentication y antes de conservar ningún perfil, puede requerirse recuperación administrativa.

El cierre usa `FirebaseAuth.signOut()` y borra la fila de sesión únicamente en esa instalación; no revoca los tokens de otros teléfonos. Restablecer la contraseña usa el correo de Firebase y sus reglas de revocación, que son diferentes de un cierre de sesión. La navegación privada sigue condicionada a la sesión: no se han alterado los destinos de otros módulos.

El registro actual no solicita teléfono. Por tanto no se añade un campo nuevo ni un selector innecesario. El teléfono opcional de Perfil pertenece al miembro 4 y mantiene su formulario existente.

## Teléfonos internacionales en empleados

El requisito de teléfono se aplica al alta de empleados y a su edición, ambas en `HomeEntryForm`. Se sustituye la entrada que eliminaba el signo `+` y exigía entre 9 y 15 dígitos por un selector de país/prefijo y un campo de teléfono:

- Selección inicial `🇪🇸 +34 · España`, con búsqueda de otros países por nombre, código o prefijo.
- Validación de longitud y numeración por país con `libphonenumber` de Google, versión 9.0.40, sin exigir nueve dígitos a todos los teléfonos.
- Guardado en E.164, por ejemplo `+34612345678`, en la columna `telefono` existente, sin cambiar el esquema ni migrar masivamente datos antiguos.
- Al editar se recupera el país del prefijo internacional. Los números antiguos sin prefijo se muestran íntegros, con España como selección inicial, y se normalizan al guardar si son válidos.
- Pegar un número internacional completo con `+` o `00` selecciona su país. Los ceros nacionales se interpretan según sus metadatos, incluidos los ceros significativos italianos.
- Un teléfono inválido queda señalado en rojo, recibe foco al llegar a su validación y bloquea el guardado, conservando los datos introducidos.
- País y número se conservan con `rememberSaveable` durante la restauración del formulario.

Se corrigió además `whatsappNumber`: un prefijo explícito ya no se modifica por tener nueve dígitos totales. El supuesto anterior podía anteponer `34` a un contacto de Islas Feroe como `+298211234`. Se mantiene la compatibilidad con contactos nacionales antiguos sin prefijo. No se han cambiado las pantallas de mensajería.

Referencias: [biblioteca oficial de Google](https://github.com/google/libphonenumber), [versión utilizada](https://github.com/google/libphonenumber/releases/tag/v9.0.40) y [plan E.164 de la UIT](https://www.itu.int/rec/T-REC-E.164).

## Activación del backend

1. Mantener el mismo `google-services.json` y proyecto Firebase en los dos APK. El cliente incluido apunta al proyecto del equipo; no se han añadido credenciales de administrador.
2. Habilitar Cloud Firestore Standard, base `(default)`, en ese mismo proyecto. Elegir su ubicación con el equipo.
3. Revisar `firestore.rules` y publicarlas en Firebase. Si el proyecto ya tiene reglas para otras colecciones, integrar este bloque con ellas antes de desplegar: el archivo preparado deniega por defecto el resto. No publicar reglas abiertas de modo de prueba.
4. Comando de despliegue, después de revisar el proyecto y las reglas existentes: `firebase deploy --only firestore:rules --project <ID_DEL_PROYECTO_DEL_EQUIPO>`.
5. Distribuir el mismo APK actualizado en ambos teléfonos e iniciar sesión en el original para migrar perfiles antiguos.

Actualización del 26/11/2026: con confirmación expresa se creó Cloud Firestore Standard `(default)` en `europe-southwest1` (Madrid), proyecto `gestor-empresa-74d8b`, manteniendo Spark y sin habilitar extras de pago. Se publicaron las reglas de `firestore.rules`, verificando la versión activa en la consola. El acceso anónimo al documento de diagnóstico devuelve `403 Missing or insufficient permissions`; la API ya no devuelve servicio desactivado. Se conserva el mismo proveedor y proyecto de Authentication. El diagnóstico y la corrección del certificado del emulador se documentan en [INCIDENCIA_ACCESO.md](INCIDENCIA_ACCESO.md).

## Exclusión de pruebas de la entrega

Por petición de la usuaria, se retiran de la entrega todos los archivos de pruebas añadidos durante esta revisión, los cambios realizados en las pruebas existentes y las herramientas de prueba asociadas. Se conserva una copia local en `.local/excluded-tests/`, ignorada por Git. Las pruebas previas del repositorio se mantienen sin nuestras modificaciones. Los resultados, comandos y listas de pruebas de este informe documentan las ejecuciones anteriores a esta exclusión; no describen una suite nueva incluida en la entrega final. Se conservan el código de la aplicación, los esquemas de producción 6 y 7 y las reglas de seguridad.

## Verificación histórica

Resultados obtenidos en esta tarea:

| Comprobación | Resultado |
| --- | --- |
| `assembleDebug`, APK normal con configuración Firebase del equipo | Superada. Copia conservada en `build/entrega/gestor-empresa-debug.apk`. |
| `lintDebug` | Superada: 0 errores, 53 advertencias. |
| Suite unitaria completa | 107/107 aprobadas, 0 fallos y 0 omitidas, tras incorporar los teléfonos de empleados. `testDebugUnitTest assembleDebug lintDebug` termina con `BUILD SUCCESSFUL`. |
| Autenticación, validación, ViewModel, hashes, sesiones y migraciones | 53/53 aprobadas, incluidas 9 pruebas con dos bases locales y dos clientes de autenticación simulados independientes. |
| `ProfileRepositoryTest` | 5/5 aprobadas. |
| Teléfonos internacionales | 9/9 pruebas unitarias aprobadas: prefijos, países, distintas longitudes, ceros, edición, pegado, validación y búsqueda. También se amplía la prueba existente de WhatsApp para conservar los prefijos internacionales cortos. |
| Formulario Android de empleados | 3/3 aprobadas en API 36 mediante runner directo, `OK (3 tests)`, 37,563 segundos, salida 0. Alta española y E.164, país y número conservados tras restauración con ocho dígitos daneses, y edición extranjera con foco/error y bloqueo de guardado inválido. Se verifica persistencia en Room. |
| Reglas de Firestore contra el emulador oficial | 5/5 aprobadas: aislamiento entre usuarios, anonimato, identidad de empresa, campos permitidos y validación. |

Se corrigió la fixture de `ModuleCatalogTest.summarySeparatesUpcomingExpiredAndResolvedRecords`: esperaba un curso próximo el 10/11 partiendo del 24/10, aunque el umbral de `summary` es de 15 días y hay 17 días entre ambas fechas. Ahora calcula un curso a `today.plusDays(15)` y otro a `today.plusDays(16)`, comprobando que el primero se incluye y el segundo se excluye. Se mantienen todas las aserciones. La regla de avisos no se ha modificado y la prueba corregida pasa dentro de la suite completa.

La compilación de la suite Android completa también detectó errores previos en `HomeModuleUiTest` (líneas 60, 82 y 114): faltan callbacks nuevos de `CatalogScreen` y cambió la firma de `DetailScreen`. No se corrigieron módulos ajenos. `tools/auth-tests.init.gradle`, solo cuando se invoca explícitamente, limita las fuentes instrumentadas al paquete de autenticación. Su APK de tests compila correctamente.

Comandos reproducibles:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
.\gradlew.bat assembleDebugAndroidTest '-PhomeUiTests=true' '-PfirebaseAuthEmulatorHost=10.0.2.2' -I tools/auth-tests.init.gradle
```

El emulador Firestore usa 8080 y Auth 9099. Para pruebas Android contra ambos: `-PfirebaseAuthEmulatorHost=10.0.2.2`; nunca usar esta configuración en APK de producción. Los tests de interfaz usan el paquete `.uitests` para proteger los datos de desarrollo.

Por petición de la usuaria, la carpeta de pruebas `tools/firebase-tests/` se excluye del repositorio. Los resultados de reglas indicados arriba corresponden a las comprobaciones realizadas antes de retirarla. Se mantienen las reglas de seguridad de la aplicación en `firestore.rules`.

La variante instrumentada usa exactamente `com.example.gestorempresa.uitests.pruebas`. Para ejecutar las dos clases con ambos emuladores arrancados:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-PhomeUiTests=true' '-PfirebaseAuthEmulatorHost=10.0.2.2' -I tools/auth-tests.init.gradle '-Pandroid.testInstrumentationRunnerArguments.class=com.example.gestorempresa.auth.RegisterValidationUiTest,com.example.gestorempresa.auth.FirebaseRepositoryEmulatorTest'
```

En este equipo, el runner Android devolvió `OK (2 tests)` y el resultado UTP marcó las dos pruebas como `PASSED`, pero Gradle falló al recibir el informe UTP (`Failed to receive the UTP test results`). No se considera aprobado ese task de Gradle. Se conserva el log del runner en `build/entrega/android-auth-test-results.log`. Las pruebas comprueban foco/desplazamiento al primer campo incorrecto y registro, acceso, recuperación por correo y descarga de perfil desde una base vacía con los adaptadores Android reales contra Auth + Firestore Emulator. No usan el proyecto real ni dos teléfonos.

Se confirmó después el resultado ejecutando directamente `adb shell am instrument`: `OK (2 tests)`, 20,037 segundos, salida 0 del comando. Para repetirlo, instalar los APK aislados de app y androidTest (Gradle puede desinstalarlos al terminar) y, con Auth y Firestore Emulator arrancados, ejecutar:

```powershell
adb -s emulator-5580 shell am instrument -w -r -e class com.example.gestorempresa.auth.RegisterValidationUiTest,com.example.gestorempresa.auth.FirebaseRepositoryEmulatorTest com.example.gestorempresa.uitests.pruebas.test/androidx.test.runner.AndroidJUnitRunner
```

El emulador Android utilizado fue API 36, sin ventana, en modo `-read-only`, con el paquete independiente de pruebas. Se cerraron los emuladores iniciados por esta tarea al finalizar.

Los tests Android de teléfonos de empleados se compilan con el mismo init script y `-PfocusedAndroidTests=phone`. No requieren emuladores Firebase. El paquete aislado en este caso es `com.example.gestorempresa.uitests`:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest '-PhomeUiTests=true' '-PfocusedAndroidTests=phone' -I tools/auth-tests.init.gradle
# Instalar los APK aislados de app y androidTest en el emulador, y ejecutar:
adb -s emulator-5580 shell am instrument -w -r -e class com.example.gestorempresa.phone.EmployeePhoneUiTest com.example.gestorempresa.uitests.test/androidx.test.runner.AndroidJUnitRunner
```

La ampliación de teléfonos se compiló y probó con una nueva salida local `android-phone-build` en la misma carpeta de visualizaciones autorizada, evitando un JAR bloqueado de la salida anterior. Se utilizó `--no-daemon -Pkotlin.compiler.execution.strategy=in-process` para liberar los archivos al terminar. La copia del APK normal de entrega y los informes de `app/build/reports` del worktree se actualizaron con esta versión.

El log de las tres pruebas de empleados queda en `build/entrega/employee-phone-test-results.log`. Se ejecutaron con bases Room en memoria y datos de prueba, sin enviar mensajes ni realizar llamadas. Queda pendiente una revisión visual en teléfonos físicos con distintos tamaños y ajustes de letra.

En este equipo, Java necesitó confiar en el almacén de Windows para descargar dependencias. La ejecución local usa JDK 17 y los argumentos `'-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE'`. No se ha desactivado HTTPS ni se ha cambiado esa configuración en el proyecto.

Para que la sincronización de Android Studio también use esos certificados sin depender de argumentos de consola, se ha creado la configuración local `C:/Users/irene/.gradle/gradle.properties`:

```properties
systemProp.javax.net.ssl.trustStoreType=Windows-ROOT
systemProp.javax.net.ssl.trustStore=NONE
```

Esta configuración es específica de este equipo Windows, afecta a sus ejecuciones de Gradle y no se sube al repositorio. Se mantiene la validación de certificados HTTPS. Corrige el fallo `PKIX path building failed` al descargar `gradle-8.13-src.zip` durante la importación; no requiere actualizar Gradle ni las dependencias de la aplicación. La tarea `prepareKotlinBuildScriptModel` vuelve a terminar correctamente sin pasar opciones SSL por consola.

Comprobación adicional en Android Studio: la sincronización completa termina en verde el 26/11/2026 a las 19:16 (7 min 12 s, con descarga inicial de fuentes de Gradle y bibliotecas), sin los 24 errores anteriores. Quedan avisos informativos de nuevas versiones de Gradle/Android Gradle Plugin; no se han aplicado actualizaciones de versiones.

Para las suites Robolectric se descargó el SDK 28 mediante Gradle y se utilizó el init script local `build/robolectric-local.init.gradle` con `-I`, evitando una descarga secundaria de Robolectric que fallaba por certificados. La fixture del esquema 2 se añadió al repositorio. OneDrive bloqueó archivos generados al cambiar de variante; la salida instrumentada se redirigió mediante el `local.properties` ignorado a `C:/Users/irene/.codex/visualizations/2026/11/26/01a217bb-c117-7d70-b495-593baebd2d02/android-auth-build`. Los informes unitarios y lint de la compilación normal permanecen en `app/build/reports` del worktree.

## Pruebas pendientes con dos teléfonos reales

1. Registrar en A; entrar en B sin registrar de nuevo. Comparar UID, `remoteId`, nombre y empresa. Repetir el acceso y comprobar que no aparecen duplicados en Authentication ni Firestore.
2. Cerrar sesión en A y usar B. En A, Atrás y reapertura no deben recuperar contenido privado. Repetir al revés.
3. Solicitar el correo desde B, abrir el enlace en otro dispositivo y cambiar la contraseña. Verificar contraseña antigua rechazada y nueva aceptada en A/B; conservar UID y empresa. Comprobar recepción real, enlace de un uso y revocación de la sesión anterior según Firebase.
4. Cerrar el proceso y reiniciar el teléfono con sesión válida; reabrir sin contraseña. Deshabilitar/revocar una cuenta desde la consola y comprobar que desaparece el contenido privado cuando el SDK detecta la revocación.
5. Probar inicio sin Internet y recuperación tras restablecer conexión. No debe decir contraseña incorrecta por un fallo de red. Una sesión existente puede conservar acceso local durante la desconexión: la revocación remota no se puede conocer sin red.
6. Migrar una cuenta local antigua en A y entrar después en B. Probar actualización sobre la versión anterior conservando datos de Inicio, Perfil y Ajustes.
7. Editar Perfil en A y volver a iniciar sesión en B; comprobar nombre, empresa y datos editables. Foto, preferencias y registros de otros módulos no están incluidos en esta sincronización.
8. Revisar registro con teclado, pantalla pequeña y tamaño de letra grande: todos los errores en rojo, foco/desplazamiento al primero y conservación de los campos válidos.

Mejoras propuestas fuera de esta corrección: sincronización de los registros de otros módulos, fotos compartidas, invitaciones multiusuario y resolución de ediciones concurrentes. No se declaran implementadas.

## Archivos modificados o añadidos

Rutas relativas a la carpeta `auth-multidispositivo`. Los artefactos de compilación y `local.properties` son locales y están excluidos de Git.

- `.gitignore`
- `app/build.gradle.kts`
- `app/schemas/com.example.gestorempresa.data.local.AppDatabase/2.json`
- `app/schemas/com.example.gestorempresa.data.local.AppDatabase/6.json`
- `app/src/androidTest/java/com/example/gestorempresa/auth/FirebaseRepositoryEmulatorTest.kt`
- `app/src/androidTest/java/com/example/gestorempresa/auth/RegisterValidationUiTest.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/main/java/com/example/gestorempresa/data/auth/FirebaseAuthRepository.kt`
- `app/src/main/java/com/example/gestorempresa/data/auth/FirebaseEmailAuthService.kt`
- `app/src/main/java/com/example/gestorempresa/data/auth/FirestorePerfilCuentaService.kt`
- `app/src/main/java/com/example/gestorempresa/data/auth/RoomAuthRepository.kt`
- `app/src/main/java/com/example/gestorempresa/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/gestorempresa/data/local/AuthMigration.kt`
- `app/src/main/java/com/example/gestorempresa/data/local/dao/AuthDao.kt`
- `app/src/main/java/com/example/gestorempresa/data/local/dao/PerfilDao.kt`
- `app/src/main/java/com/example/gestorempresa/data/local/entities/EmpresaEntity.kt`
- `app/src/main/java/com/example/gestorempresa/data/profile/RoomProfileRepository.kt`
- `app/src/main/java/com/example/gestorempresa/di/AuthModule.kt`
- `app/src/main/java/com/example/gestorempresa/domain/auth/AuthModels.kt`
- `app/src/main/java/com/example/gestorempresa/domain/auth/AuthValidator.kt`
- `app/src/main/java/com/example/gestorempresa/domain/auth/PerfilCuentaService.kt`
- `app/src/main/java/com/example/gestorempresa/ui/auth/AuthComponents.kt`
- `app/src/main/java/com/example/gestorempresa/ui/auth/AuthViewModel.kt`
- `app/src/main/java/com/example/gestorempresa/ui/auth/LoginScreen.kt`
- `app/src/main/java/com/example/gestorempresa/ui/auth/RegisterScreen.kt`
- `app/src/test/java/com/example/gestorempresa/auth/AuthValidatorTest.kt`
- `app/src/test/java/com/example/gestorempresa/auth/AuthViewModelTest.kt`
- `app/src/test/java/com/example/gestorempresa/auth/DatabaseMigrationTest.kt`
- `app/src/test/java/com/example/gestorempresa/auth/FakePerfilCuentaService.kt`
- `app/src/test/java/com/example/gestorempresa/auth/FirebaseAuthRepositoryTest.kt`
- `app/src/test/java/com/example/gestorempresa/auth/MultidispositivoTest.kt`
- `app/src/test/java/com/example/gestorempresa/auth/PasswordHasherTest.kt`
- `app/src/test/java/com/example/gestorempresa/auth/RoomAuthRepositoryTest.kt`
- `app/src/test/java/com/example/gestorempresa/data/profile/ProfileRepositoryTest.kt`
- `app/src/test/java/com/example/gestorempresa/ui/home/ModuleCatalogTest.kt`
- `docs/REVISION_AUTENTICACION.md`
- `firebase.json`
- `firestore.rules`
- `gradle/libs.versions.toml`
- `tools/auth-tests.init.gradle`

Archivos de la ampliación de teléfonos de empleados (además de las dependencias Gradle y el selector de tests ya enumerados):

- `app/src/main/java/com/example/gestorempresa/domain/phone/InternationalPhones.kt`
- `app/src/main/java/com/example/gestorempresa/ui/home/InternationalPhoneField.kt`
- `app/src/main/java/com/example/gestorempresa/ui/home/HomeEntryForm.kt`
- `app/src/main/java/com/example/gestorempresa/ui/advanced/communication/MessageTemplates.kt`
- `app/src/test/java/com/example/gestorempresa/domain/phone/InternationalPhonesTest.kt`
- `app/src/test/java/com/example/gestorempresa/ui/advanced/AdvancedFeaturesTest.kt`
- `app/src/androidTest/java/com/example/gestorempresa/phone/EmployeePhoneUiTest.kt`

## Referencias técnicas

- [Gestión de usuarios Android y restablecimiento de contraseña](https://firebase.google.com/docs/auth/android/manage-users).
- [Renovación y revocación de sesiones de Firebase](https://firebase.google.com/docs/auth/admin/manage-sessions).
- [Transacciones de Firestore](https://firebase.google.com/docs/firestore/manage-data/transactions).
- [Reglas de acceso basadas en identidad](https://firebase.google.com/docs/firestore/security/rules-conditions).
