# Validación de la rama integrada

Fecha: **3 de noviembre de 2026** (America/Santiago).

Rama: `release/validacion`. Código comprobado: `9a625ddae666cfebc7897d751e107c791380753c`, copiado de `feature/administracion-ti`. Esta versión incluye los commits de expediente funcionario, gestión administrativa y servicios y seguridad. Los cambios de esta validación añaden documentación y evidencias; el código de la aplicación permanece en esa versión.

## Resultados de esta ejecución

| Comprobación | Resultado |
| --- | --- |
| Aplicación de desarrollo (`assembleDebug`) | APK generado e instalado |
| APK de pruebas (`assembleDebugAndroidTest`) | Generado e instalado |
| Pruebas unitarias (`testDebugUnitTest`) | 24 aprobadas, 0 fallos, 0 errores, 0 omitidas |
| Pruebas en emulador | 10 aprobadas, 0 fallos, 0 omitidas; 564,862 segundos |
| Android Lint (`lintDebug`) | 0 errores y 26 advertencias |

La suite de emulador incluye el circuito documental entre cuatro roles, solicitudes, auditoría, incidentes, usuarios y permisos, respaldo y recuperación y validación de fechas. La salida termina en `OK (10 tests)`.

Evidencias de esta ejecución: [salida del emulador](evidencias/validacion-rama-2026-11-03-emulador.log) y [resumen con resultados y advertencias](evidencias/validacion-rama-2026-11-03.json). El [informe de entrega anterior](VERIFICACION.md) conserva su fecha y sus resultados originales.

## Entorno y reproducción

Windows, Gradle Wrapper 9.5.0, JVM de Gradle Eclipse Adoptium 25.0.3 y Android SDK 37. Emulador AVD `Pixel_10a`, modelo `sdk_gphone16k_x86_64`, Android 17, API 37. Se usaron exclusivamente datos ficticios.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --max-workers=2
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w cl.fourpeople.aps.test/androidx.test.runner.AndroidJUnitRunner
```

La primera ejecución de Lint encontró un formato incorrecto en la ruta del SDK de `local.properties`, creado para este computador e ignorado por Git. Se corrigió la ruta y se repitieron explícitamente el análisis y el informe para evitar reutilizar ese resultado:

```powershell
.\gradlew.bat :app:lintAnalyzeDebug --rerun :app:lintReportDebug --rerun :app:lintDebug --max-workers=2
```

Esta segunda comprobación terminó en `BUILD SUCCESSFUL`, sin errores. No se cambió código de la aplicación para obtener los resultados.

SHA-256 del APK generado: `b53902d3c5c9fbb99ab82ecb8ba9083a4caf38831a0845ced340fdf19828a7af`.

## Límites y pendientes

La validación confirma la compilación y los recorridos cubiertos por las pruebas en este entorno. No certifica todos los dispositivos ni una aplicación lista para producción. No se probaron un dispositivo físico, Android 8, la cámara física, la interfaz manual del selector de archivos ni los diálogos de exportación. El circuito automático usa un PDF ficticio como respuesta controlada del selector y ejecuta realmente su importación y persistencia.

El emulador mostró un aviso de que System UI no respondía. Se seleccionó **Esperar** y la misma suite terminó completa, sin excluir ni repetir pruebas para ocultar fallos.

Siguen pendientes las 26 advertencias de Lint: UseKtx (11), UnusedResources (7), GradleDependency (3), ExifInterface (2), RedundantLabel (1), AndroidGradlePluginVersion (1) y NewerVersionAvailable (1). Revisar especialmente la alternativa AndroidX a ExifInterface y las dependencias antes de una entrega de producción.
