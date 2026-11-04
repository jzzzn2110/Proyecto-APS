# Proyecto APS

MVP académico para gestionar documentos y solicitudes de funcionarios de Atención Primaria de Salud. Aplicación Android en Kotlin y Jetpack Compose, con datos ficticios y almacenamiento local.

## Estado de esta rama

`release/validacion` reúne el trabajo integrado de `feature/administracion-ti`, que incluye las ramas anteriores. El código `9a625dd` se comprobó el **3 de noviembre de 2026**: aplicación y APK de pruebas compilados, **24 pruebas unitarias y 10 pruebas en emulador aprobadas**, sin fallos ni pruebas omitidas. Android Lint terminó con **0 errores y 26 advertencias pendientes**.

Consultar el [informe de validación de esta rama](docs/VALIDACION_RAMA.md) para conocer el entorno, las evidencias y los límites de las comprobaciones. Los resultados corresponden al MVP y a los recorridos probados.

## Descripción

El proyecto reúne los flujos de Funcionario, Administrativo, Jefatura y TI / Admin. TI permite consultar auditoría, exportar CSV, gestionar incidentes, administrar cuentas y permisos y crear o restaurar respaldos ZIP.

## Ejecutar el proyecto

1. Abrir esta carpeta en Android Studio y sincronizar Gradle.
2. Instalar Android SDK Platform 37 y aceptar sus licencias. Gradle solicita una JVM 25 mediante `gradle/gradle-daemon-jvm.properties`; la primera sincronización puede descargarla. La compatibilidad del código Java se configura en 11.
3. Crear un emulador o conectar un dispositivo con Android 8.0 o superior (API 26).
4. Ejecutar la configuración `app`.

El SDK local se configura en `local.properties`, que no se incluye en Git. Se usa el Gradle Wrapper del repositorio, sin instalar Gradle globalmente. Las descargas iniciales requieren conexión.

### Cuentas de demostración

Todas usan la contraseña **`Contraseña123#`**. Seleccionar el rol correspondiente al ingresar.

| Usuario | Rol | Uso principal |
| --- | --- | --- |
| `c.rojas` | Funcionario | Expediente ficticio de Camila, documentos y solicitudes |
| `administrativo` | Administrativo | Revisar, rechazar y aprobar documentos |
| `jefatura` | Jefatura | Revisar y resolver solicitudes |
| `d.herrera` | TI / Admin | Auditoría, incidentes, usuarios y respaldos |

Las cuentas nuevas de Funcionario no heredan el expediente de Camila. Este MVP contiene un expediente de demostración; crear una cuenta no crea un expediente adicional. Una cuenta inactiva o bloqueada no puede iniciar sesión. El rol elegido debe coincidir con el de la cuenta.

## Recorrido de demostración

1. Como Funcionario, cargar un PDF o imagen con nombre, tipo y vencimiento opcional.
2. Como Administrativo, abrirlo y rechazarlo con una observación.
3. Como Funcionario, corregir el mismo documento; se conserva su ID y la versión anterior.
4. Como Administrativo, aprobar la versión corregida.
5. Como Funcionario, enviar una solicitud con fechas, motivo y respaldo opcional.
6. Como Jefatura, resolverla con una respuesta. El Funcionario puede consultar el resultado.
7. Como TI, filtrar la auditoría por documento, consultar su historial y exportar el CSV.
8. En Incidentes, vincular una cuenta o evento y avanzar de Abierto a En revisión y Cerrado. Bloquear una cuenta es independiente de desactivarla.
9. En Usuarios, crear o editar cuentas, centro, permisos y estado. No se permite eliminar el último administrador habilitado ni retirar el acceso del administrador que realiza la operación.
10. En Configuración, ajustar los días de permisos y feriado, exportar un respaldo y restaurarlo. La restauración cierra la sesión.

Usar únicamente archivos y antecedentes ficticios. La cámara y el selector de archivos están disponibles en los dispositivos que los soportan. La importación admite PDF e imágenes de hasta 20 MB.

## Compilar y verificar

Desde PowerShell, en la raíz del repositorio y con Java y Android SDK configurados:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --max-workers=2
.\gradlew.bat connectedDebugAndroidTest --max-workers=2
```

La segunda orden requiere un emulador o dispositivo conectado. En Linux o macOS se usa `./gradlew`.

El APK de desarrollo queda en `app/build/outputs/apk/debug/app-debug.apk`. Puede instalarse desde Android Studio o con `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Es una compilación de demostración firmada con la clave de desarrollo, no una versión para publicar.

La [verificación de entrega](docs/VERIFICACION.md) registra resultados, entorno y límites de las pruebas. El [informe del proyecto](docs/INFORME_PROYECTO_APS.md) presenta alcance y trazabilidad. Las [evidencias](docs/evidencias/) muestran ejecuciones reales; no son los mockups originales.

## Organización y persistencia

Los archivos Kotlin están en `app/src/main/java/cl/fourpeople/aps`. `MainActivity` controla sesión y navegación; las pantallas Compose corresponden a cada rol. Los modelos y reglas se separan de los componentes de persistencia local.

| Componente | Responsabilidad |
| --- | --- |
| `ControlAcceso` | Verificar cuenta, rol, permisos, centro y titular del expediente |
| `DatosFuncionario` y `PersistenciaDocumentos` | Documentos, versiones y expediente |
| `DatosSolicitudes` y `ReglasSolicitudes` | Solicitudes, estados, solapamientos y saldos |
| `DatosSeguridad` | Incidentes y bloqueo de cuentas |
| `DatosUsuarios` | Usuarios, permisos y política de días |
| `AuditoriaLocal` | Reunir eventos y generar consultas y CSV |
| `RespaldoLocal` | Exportar, validar, restaurar y recuperar respaldos |

Los datos se guardan en el almacenamiento privado de Android: `expediente_funcionario.json`, `solicitudes_funcionario.json`, `seguridad_ti.json`, `usuarios_ti.json` y los adjuntos en `documentos/`. Desinstalar la aplicación elimina sus datos locales; exportar un respaldo antes si se desea conservar la demostración.

El respaldo ZIP incluye los cuatro JSON, los adjuntos actuales e históricos y un manifiesto SHA-256. Se validan rutas, tamaños, integridad, referencias y la existencia de un administrador habilitado antes de aplicar la restauración. El límite es 200 MB y 2000 archivos de datos, más el manifiesto. Se conserva la auditoría actual al combinar eventos por ID y se registra la restauración. Un registro de recuperación permite revertir una restauración interrumpida al iniciar la aplicación. Los respaldos son manuales y no incluyen la sesión.

## Alcance y pendientes

La autenticación es de demostración. No hay integración con SharePoint, Microsoft 365, servidor institucional ni sincronización entre dispositivos. Los permisos se comprueban dentro de la aplicación; no sustituyen autorización en un servidor.

Los cuatro roles implementados cubren el circuito descrito. Las pantallas de RRHH y Supervisor del contexto amplio quedan pendientes. La búsqueda corresponde al expediente de demostración y no a un repositorio institucional con múltiples expedientes. La aprobación documental corresponde a Administrativo; Jefatura resuelve solicitudes.

Los avisos de vencimiento se muestran dentro de la aplicación; no hay notificaciones programadas de Android. Tampoco se implementaron cierre por inactividad, cifrado propio de los archivos ni respaldos periódicos. La auditoría es de solo lectura en la interfaz, pero no ofrece inmutabilidad garantizada por un servidor y no registra todas las previsualizaciones ni intentos fallidos de acceso.

## Equipo y referencias

Lucas Borquez, Jhon Olivares, Pablo Salas y Benjamin Villalón. Proyecto de Desarrollo de Aplicaciones Móviles.

Referencias de trabajo: documento «Análisis del contexto asignado», pauta interna «Pauta de trabajo APS 4People» y mockups 22 a 26 para TI. La pauta interna orienta la organización del equipo; no se presenta como una rúbrica oficial del docente.
