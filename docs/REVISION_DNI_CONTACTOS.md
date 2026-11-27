# DNI de empleados y teléfonos de contactos

## Diagnóstico

- **DNI de la captura:** cumple el formato de ocho números y una letra, pero no el control matemático. El número tiene resto 13 al dividirse entre 23; la tabla oficial asigna **J** a ese resto, en vez de la letra introducida. Debe comprobarse el documento original, porque el error también podría estar en los números. No se incorpora el identificador de la captura a las fixtures del repositorio.
- **Validación de DNI:** el algoritmo existente era correcto. El mensaje no distinguía formato y letra de control; se mejora la explicación y se conserva el documento escrito para que lo corrija la persona, sin cambiar automáticamente la letra.
- **Altas de proveedores y subcontratados:** usaban un texto simple y una longitud de 9 a 15 caracteres. Esa regla rechazaba teléfonos internacionales válidos, como un número danés de ocho cifras, y no guardaba un prefijo coherente.
- **Edición de contactos:** tampoco ofrecía selector ni validación del número internacional.
- **Error adicional de identificación del registro:** el editor consultaba todas las tablas por el mismo ID y elegía el primer dato no nulo. Un proyecto, un proveedor y un subcontratado pueden compartir el ID 1. Esto podía llenar el formulario de un proveedor con nombre o teléfono de otro tipo de registro. Ahora la búsqueda se limita al tipo seleccionado.

## Implementación

- DNI/NIE: mensajes diferentes para campo vacío, formato incorrecto y formato correcto con letra de control incorrecta. Se mantiene el foco y el desplazamiento al campo, el bloqueo de guardado y el control para DNI y NIE X/Y/Z.
- Altas y edición de proveedores/subcontratados: componente compartido `InternationalPhoneField`, inicialmente España 🇪🇸 +34, búsqueda de países y prefijos y teclado telefónico.
- Se reutiliza `InternationalPhones` y libphonenumber, sin otra lista de países ni otra regla de longitud.
- El teléfono conserva su carácter opcional. Si se introduce, debe ser válido para el prefijo seleccionado; el error aparece en el campo y se lleva el foco a él. Los demás valores permanecen en el formulario.
- Al guardar se usa E.164, por ejemplo `+442079460018` o `+4532123456`. Al editar se recuperan país y número nacional a partir del número internacional guardado. Los números antiguos sin prefijo mantienen su texto y muestran España inicialmente para poder revisarlos.
- El borrador de país y número se conserva al recrear el formulario. No se cambia el esquema de Room ni se crean otros registros para actualizar un contacto.

## Archivos

- `app/src/main/java/com/example/gestorempresa/ui/home/HomeEntryForm.kt`.
- `app/src/main/java/com/example/gestorempresa/ui/home/ModuleRecordForm.kt`.
- `app/src/main/java/com/example/gestorempresa/ui/home/EditCatalogItemScreen.kt`.
- `app/src/test/java/com/example/gestorempresa/ui/home/SpanishDniValidationTest.kt`.
- `app/src/androidTest/java/com/example/gestorempresa/phone/EmployeePhoneUiTest.kt`.
- `app/src/androidTest/java/com/example/gestorempresa/phone/CatalogPhoneUiTest.kt`.
- Este informe.

## Exclusión de pruebas de la entrega

Por petición de la usuaria, los archivos nuevos y las modificaciones de pruebas se excluyen de la entrega final. Se conserva una copia local ignorada en `.local/excluded-tests/`. Las listas de archivos de prueba, resultados y comandos de este informe describen las comprobaciones anteriores a esa exclusión; las correcciones de la aplicación se mantienen.

## Verificación histórica

- Compilación normal `assembleDebug`: superada.
- Suite unitaria completa `testDebugUnitTest`: **131/131**, sin fallos, errores ni pruebas omitidas, incluidos los tres casos nuevos de DNI/NIE. Se repitió tras sustituir los ejemplos iniciales por el ejemplo público del Ministerio.
- Pruebas Android de formularios: **9/9** en el emulador API 36 (`EmployeePhoneUiTest`: 4; `CatalogPhoneUiTest`: 5). El runner termina con `OK (9 tests)` en 129,038 segundos. Se comprueban el guardado real en Room, el foco y el error del campo, los borradores y la edición sin mezclar tablas.
- `git diff --check`: superado.

APK de esta revisión, anterior a la reparación posterior de acceso: SHA-256 `F62D3D5D4064A4924D99FEF22B8F12B4EBA2776DCA2FC1207F61D1261C6E93A6`, conservado como `build/entrega/gestor-empresa-1.0-debug.apk`. La copia `gestor-empresa-debug.apk` se actualiza con las entregas posteriores; la versión 1.1 y su verificación se describen en [REVISION_AUTENTICACION.md](REVISION_AUTENTICACION.md).

Informe unitario: `build/entrega/dni-contactos-unit-tests/index.html`.

Salida de las pruebas Android: `build/entrega/dni-contactos-android-results.log`. El APK normal, con el identificador `com.example.gestorempresa`, se reinstaló mediante `adb install -r` en `emulator-5554`, sin borrar los datos guardados. Las dos compilaciones (normal y de pruebas) terminaron con `BUILD SUCCESSFUL`.

No se ha repetido lint ni ejecutado la suite instrumentada completa de todos los módulos en esta revisión. No se ha comprobado en teléfonos físicos ni probado todos los países del selector.

Las pruebas añadidas cubren formato correcto con control incorrecto mediante el ejemplo público `12345678N` frente a `12345678Z`, formato y control de NIE, país inicial, selección de Reino Unido y Dinamarca, ocho dígitos, guardado E.164 en Room, bloqueo y foco de números inválidos, teléfonos vacíos opcionales, restauración del borrador y edición con IDs coincidentes de distintas tablas.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -I build/robolectric-local.init.gradle
.\gradlew.bat assembleDebug assembleDebugAndroidTest '-PhomeUiTests=true' '-PfocusedAndroidTests=phone' -I tools/auth-tests.init.gradle
adb -s emulator-5554 shell am instrument -w -r -e class com.example.gestorempresa.phone.EmployeePhoneUiTest,com.example.gestorempresa.phone.CatalogPhoneUiTest com.example.gestorempresa.uitests.test/androidx.test.runner.AndroidJUnitRunner
```

Las pruebas Android se compilan para la aplicación aislada `.uitests` y usan bases Room en memoria; no escriben en las empresas ni cuentas de la app de uso. El init script excluye las fuentes de la antigua suite instrumentada de Inicio que no compilan con las firmas actuales, como ya se documentó en la revisión de autenticación.

Referencia: [Ministerio del Interior, cálculo del dígito de control del NIF/NIE](https://www.interior.gob.es/opencms/ca/servicios-al-ciudadano/tramites-y-gestiones/dni/calculo-del-digito-de-control-del-nif-nie/index.html).
