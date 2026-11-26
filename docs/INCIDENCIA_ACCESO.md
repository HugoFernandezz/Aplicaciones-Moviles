# Incidencia de acceso y certificado — 26/11/2026

## Causas comprobadas

1. Firebase Authentication devolvía `CertPathValidatorException: Trust anchor for certification path not found`. El mensaje general de la interfaz no identificaba la causa de transporte. Se reprodujo con el SDK real en Android, sin usar una cuenta personal.
2. La conexión a `identitytoolkit.googleapis.com` usaba la cadena pública de Google y funcionaba; la conexión a `www.googleapis.com`, también utilizada por el SDK, presentaba una cadena emitida por `AVG Web/Mail Shield Root`. La prueba Android reprodujo el rechazo de esa cadena y registró su emisor. Esa CA está instalada en Windows, pero no pertenece al almacén de confianza de Android.
3. Cloud Firestore no estaba creado ni activado en `gestor-empresa-74d8b`. La API devolvía explícitamente servicio deshabilitado. Aunque Authentication llegara a validar las credenciales, faltaba el almacenamiento compartido del perfil.

## Correcciones

- Se retiró de `LoginScreen.kt` la frase «Tu cuenta y el perfil de tu empresa se recuperan al iniciar sesión en otro dispositivo» a petición de la usuaria.
- Se creó Firestore Standard `(default)` en Madrid (`europe-southwest1`), manteniendo Spark, con autorización expresa. Se publicaron las reglas existentes del repositorio: acceso al perfil propio por UID, campos validados, sin acceso anónimo ni a perfiles ajenos y sin apertura de otras colecciones. La consola muestra la versión publicada; `build/entrega/firebase-publicado.jpg` conserva la comprobación.
- `tools/configure-local-debug-ca.ps1` exporta únicamente el certificado público de una CA que ya existe en el almacén de confianza de Windows. Verifica que sea una CA y no esté caducada. Guarda recursos y manifiesto en `.local/debug-tls`, ignorado por Git, y activa `debug.trustedCa=true` en el `local.properties` también ignorado.
- `app/build.gradle.kts` incorpora ese manifiesto y recursos **únicamente en Debug local y mediante activación explícita**. Las compilaciones que usan los emuladores Auth/Firestore mantienen su configuración específica. Release no incorpora esta CA ni la configuración local.
- La confianza adicional se limita a `www.googleapis.com`, `identitytoolkit.googleapis.com`, `securetoken.googleapis.com` y `firestore.googleapis.com`. Se mantienen las CA del sistema, la comprobación de cadena y de nombre del servidor y la prohibición de HTTP. No se instala un certificado en el dispositivo ni se modifica el antivirus.

La CA pública utilizada en este equipo tiene huella SHA-1 `46B9CC6B76E4055F88F86F67A23BF29519CE3D69`. No debe copiarse esta configuración como una decisión de confianza general para otros equipos ni incluirse en una distribución Release. Cada equipo que necesite esta configuración de desarrollo debe identificar su propia CA confiable.

```powershell
.\tools\configure-local-debug-ca.ps1 -CertificateThumbprint <HUELLA_CA_PUBLICA_CONFIABLE_DE_ESTE_EQUIPO>
```

Para desactivar el ajuste local, establecer `debug.trustedCa=false` en `local.properties` y volver a compilar.

## Comprobaciones

- Antes de la corrección: el SDK de Authentication fallaba por certificado; la prueba HTTPS fallaba al llegar a `www.googleapis.com` y mostraba el emisor AVG. Logs: `build/entrega/firebase-connection-before.log` y `build/entrega/firebase-tls-before.log`.
- Backend activado: consulta anónima de diagnóstico rechazada con `403 Missing or insufficient permissions`, en lugar de API desactivada.
- La prueba `FirebaseConnectionTest` es optativa (`-e liveFirebase true`). Comprueba los cuatro servidores HTTPS y exige que el SDK real devuelva credenciales inválidas para una identidad ficticia inexistente. No crea usuarios, no envía correos y utiliza una FirebaseApp separada de la sesión de la aplicación.
- APK Debug generado y actualizado con `adb install -r`, sin borrar los datos de la instalación. `aapt` confirma `usesCleartextTraffic=false` y la referencia a la configuración local.
- `processReleaseMainManifest` completado: el manifiesto Release no incluye `networkSecurityConfig` ni referencias a la CA de desarrollo.
- Ejecución Android posterior: **3/3 aprobadas**, `OK (3 tests)`, 21,554 s: los cuatro servidores HTTPS, respuesta real del SDK de Authentication y validación del formulario con desplazamiento al primer error. Log: `build/entrega/firebase-connection-after.log`. No se crearon cuentas técnicas en producción.
- Entrada personal confirmada por la usuaria y comprobada visualmente en la pantalla privada de Perfil, después de actualizar el APK. No se solicitaron ni introdujeron sus credenciales mediante herramientas.
- El primer intento concurrente con Gradle no llegó a iniciar las pruebas: Android registró un ANR de arranque, sin una excepción Java de la aplicación. La repetición al finalizar Gradle sí pasó completa. El arranque normal posterior terminó correctamente (`am start -W`, 5,493 s).
- Lint: cuatro errores de atributos `includeSubdomains` se corrigieron declarando explícitamente `false` para cada dominio. La comprobación final termina con `BUILD SUCCESSFUL`: **0 errores y 55 advertencias**, informe actualizado en `build/entrega/lint-results-debug.html`.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest processReleaseMainManifest lintDebug '-PfocusedAndroidTests=auth' -I tools/auth-tests.init.gradle
adb -s emulator-5554 shell am instrument -w -r -e liveFirebase true -e class com.example.gestorempresa.auth.FirebaseConnectionTest com.example.gestorempresa.test/androidx.test.runner.AndroidJUnitRunner
```

La conexión verificada con una identidad ficticia no acredita la contraseña de una cuenta personal. La entrada con la cuenta de la usuaria debe comprobarla ella misma, sin compartir sus credenciales.

## Archivos de esta incidencia

- `app/src/main/java/com/example/gestorempresa/ui/auth/LoginScreen.kt`: retirada de la frase.
- `app/build.gradle.kts`, `.gitignore`: incorporación explícita de recursos de confianza locales solo a Debug.
- `tools/configure-local-debug-ca.ps1`: generación reproducible de esa configuración local, con validación de la CA.
- `app/src/androidTest/java/com/example/gestorempresa/auth/FirebaseConnectionTest.kt`: diagnóstico optativo con HTTPS y el SDK reales.
- Este informe y actualización de `REVISION_AUTENTICACION.md` y `REVISION_PERFIL_AJUSTES.md`.
- `.local/debug-tls/` y `local.properties`: archivos locales ignorados; no se suben a Git. Las reglas remotas publicadas coinciden con `firestore.rules`, ya existente y probado.

APK de esta reparación de acceso: SHA-256 `3EA98AD1F029167A540382C74CFEB10A5BCC4DB5B87ABEA5E02C8C36012B7CDC`. La copia de entrega `build/entrega/gestor-empresa-debug.apk` se actualiza con las revisiones posteriores; la revisión de DNI y teléfonos y su verificación se documentan en [REVISION_DNI_CONTACTOS.md](REVISION_DNI_CONTACTOS.md).

Referencia: [configuración de seguridad de red de Android](https://developer.android.com/privacy-and-security/security-config), que permite declarar CA adicionales y configuraciones por dominio.
