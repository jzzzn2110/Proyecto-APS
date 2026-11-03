# Verificación de entrega

Fecha: 2 de noviembre de 2026. Rama `feature/administracion-ti`. Base `5a12b44`, más los cambios del bloque 16 todavía sin commit. La verificación corresponde al APK de esta entrega.

## Resultados

RESULTADO Compilación de aplicación y pruebas: correcta. APK de desarrollo instalado y firma Android Debug v2 verificada.

RESULTADO Pruebas unitarias: 24 aprobadas, 0 fallidas. Auditoría 6, incidentes 7, usuarios 6, reglas de solicitudes 4 y prueba de ejemplo 1.

RESULTADO Pruebas en emulador: 10 aprobadas, 0 fallidas, en 643,604 segundos. Incluyen el circuito completo entre cuatro roles, auditoría, incidentes, usuarios, respaldo y recuperación y validación de fechas.

RESULTADO Revisión de Android Lint: 0 errores y 26 advertencias. Las advertencias siguen pendientes; la entrega no se presenta como libre de advertencias.

RESULTADO Entorno: AVD Pixel 8, modelo sdk_gphone16k_x86_64, Android 17, API 37. El emulador usa GMT, por lo que las horas de las capturas difieren de la hora de Santiago.

RESULTADO APK: cl.fourpeople.aps, versión 1.0, código 1, mínimo API 26 y objetivo API 37. Tamaño 12749841 bytes. Es una compilación de desarrollo.

RESULTADO SHA256 del APK: ef568ee489d9e8878bf16c8aa9cc8e3040952e75e3ca5649bdfdb01a9451c31d

## Comandos ejecutados

Compilación y comprobaciones, con Java y SDK configurados:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --max-workers=2 --console=plain
```

Se instalaron ambos APK con `adb install -r`. La suite se ejecutó directamente para conservar las capturas:

```powershell
adb -s emulator-5554 shell am instrument -w cl.fourpeople.aps.test/androidx.test.runner.AndroidJUnitRunner
```

La salida termina en `OK (10 tests)`. Está en [pruebas-emulador.log](evidencias/pruebas-emulador.log). Los resultados XML unitarios están en [pruebas-unitarias](evidencias/pruebas-unitarias/), y el resumen verificable en [verificacion.json](evidencias/verificacion.json).

La firma se comprobó con `apksigner verify --verbose --print-certs` de build-tools 36.0.0 y los metadatos con `aapt dump badging`. El APK entregado tiene el mismo SHA256 que el generado e instalado.

## Cobertura comprobada

- Carga real de PDF, rechazo con observación, corrección del mismo ID y conservación de versiones y archivos.
- Aprobación documental por Administrativo, solicitud con respaldo, resolución por Jefatura y consulta de respuesta por Funcionario.
- Consulta desde TI, historial y contenido CSV filtrado por el documento del recorrido.
- Registro y seguimiento de incidentes vinculados, bloqueo que impide acceso y reactivación.
- Creación y edición de cuentas y permisos, desactivación y comprobaciones de rol, centro y titularidad.
- Política de días, saldos y solapamientos; fechas persistidas inválidas rechazadas.
- Respaldo con archivos PDF, restauración válida, conservación de auditoría y rechazo de ZIP dañados, incompletos o con rutas no permitidas.
- Recuperación de una restauración interrumpida.

El circuito y sus identificadores se conservan en [resultado-circuito.json](evidencias/resultado-circuito.json). Solo se usaron antecedentes ficticios.

## Límites y observaciones

El selector `ACTION_OPEN_DOCUMENT` del circuito automático devuelve un PDF ficticio mediante una respuesta controlada. La importación, copia y persistencia de la aplicación se ejecutan realmente. No se comprobó manualmente la interfaz del selector, la cámara física ni el diálogo de guardado CSV o ZIP. El contenido CSV y las operaciones de respaldo se comprueban por separado.

No se probó un dispositivo físico, Android 8 ni una conexión institucional. La versión mínima se comprueba en la configuración y en el APK; no equivale a probar todas las versiones compatibles. Tampoco se ejecutaron pruebas de carga, penetración, accesibilidad o usabilidad con personas.

Durante el arranque de la sesión, Android mostró un aviso de lentitud de System UI. Se seleccionó esperar y se dejó terminar la suite completa; no se descartaron pruebas ni se ocultaron errores. Todas las pruebas concluyeron correctamente y las capturas seleccionadas se revisaron.

Las advertencias de Lint corresponden a UseKtx (11), UnusedResources (7), GradleDependency (3), ExifInterface (2), RedundantLabel (1), AndroidGradlePluginVersion (1) y NewerVersionAvailable (1). Queda pendiente revisar especialmente la alternativa AndroidX a ExifInterface y las actualizaciones de dependencias en un bloque separado.

## Evidencias visuales

| Archivo | Contenido |
| --- | --- |
| 01-documento-cargado.png | Documento pendiente en versión 1 |
| 02-documento-corregido.png | Mismo documento corregido en versión 2 |
| 03-documento-aprobado.png | Administrativo consulta la versión 2 aprobada |
| 04-solicitud-enviada.png | Confirmación de solicitud con folio |
| 05-solicitud-aprobada.png | Funcionario consulta la solicitud aprobada |
| 06-auditoria-filtrada.png | TI filtra por el documento creado |
| 07-historial-documento.png | Secuencia de carga, consulta y rechazo |
| incidentes.png y detalle.png | Lista de incidentes y cuenta o eventos vinculados |
| cuenta-bloqueada.png e incidente-cerrado.png | Bloqueo de acceso y seguimiento del caso |
| usuarios.png y editar-usuario.png | Lista de cuentas y permisos editables |
| configuracion.png | Configuración y acciones de respaldo |

Las evidencias representan instantes del recorrido, no necesariamente el estado final de todos los datos. El APK instalado por primera vez inicia con los datos ficticios definidos en el código, sin los datos creados por estas pruebas.
