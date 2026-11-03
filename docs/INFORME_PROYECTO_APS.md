# Informe del proyecto APS

**Asignatura:** Desarrollo de Aplicaciones Móviles.  
**Equipo:** Lucas Borquez, Jhon Olivares, Pablo Salas y Benjamin Villalón.  
**Fecha:** 2 de noviembre de 2026.  
**Repositorio:** https://github.com/jzzzn2110/Proyecto-APS  
**Rama de trabajo:** `feature/administracion-ti`.

## Propósito y alcance

El proyecto implementa un MVP Android para organizar documentos y solicitudes de funcionarios de Atención Primaria de Salud. Permite cargar antecedentes, recibir observaciones, corregir versiones y consultar resoluciones. La administración TI incorpora trazabilidad, gestión de incidentes y control de cuentas, permisos y respaldos.

La demostración usa datos ficticios y un expediente de ejemplo. El funcionamiento es local: los dispositivos no comparten información y no existe conexión con SharePoint, Microsoft 365 ni un servidor institucional. El contexto original contempla más roles y funciones que los implementados en este MVP; este informe declara esas diferencias para facilitar su evaluación.

## Usuarios y funcionamiento

| Rol implementado | Cuenta inicial | Funciones principales |
| --- | --- | --- |
| Funcionario | c.rojas | Cargar y corregir documentos, consultar versiones, enviar solicitudes y revisar respuestas |
| Administrativo | administrativo | Consultar documentos, aprobar o rechazar con observación y gestionar antecedentes del expediente |
| Jefatura | jefatura | Consultar solicitudes y resolverlas con una respuesta |
| TI / Admin | d.herrera | Consultar auditoría e historial, exportar CSV, gestionar incidentes, usuarios, permisos y respaldos |

Las cuentas iniciales usan `Contraseña123#`. Es una autenticación de demostración. El rol seleccionado debe coincidir con el registrado y la cuenta debe estar activa y sin bloqueo. Los permisos, el centro y el titular se vuelven a comprobar al realizar operaciones protegidas.

Crear una cuenta de Funcionario no le asigna el expediente de Camila Rojas. La gestión de múltiples expedientes y su vinculación con nuevas cuentas requiere una ampliación del modelo actual.

## Diseño e implementación

La interfaz se construye con Kotlin y Jetpack Compose. `MainActivity` gestiona el acceso y la navegación entre pantallas. Los modelos y reglas de solicitudes se separan de los componentes de persistencia. `ControlAcceso` centraliza las comprobaciones de cuenta, permisos, centro y titularidad.

El almacenamiento privado contiene cuatro archivos JSON: expediente y documentos, solicitudes, seguridad e incidentes, y usuarios y política de días. Los PDF e imágenes se copian a una carpeta privada con nombres independientes del nombre visible del documento. Las correcciones conservan el ID documental y las referencias a las versiones anteriores.

La auditoría reúne eventos de documentos, solicitudes, seguridad y administración de usuarios. TI puede filtrarlos, consultar la secuencia del documento y generar CSV. La exportación protege las celdas frente a fórmulas y escapa los valores. La interfaz no permite editar los eventos, aunque el almacenamiento local no proporciona inmutabilidad frente a una intervención sobre el dispositivo.

El respaldo manual ZIP incluye los cuatro JSON, adjuntos y un manifiesto de hashes SHA-256. Antes de restaurar se validan estructura, rutas, tamaños, integridad y referencias. La restauración conserva eventos actuales por identificador, registra la operación y cierra la sesión. Un registro de recuperación permite revertir una restauración interrumpida. No se implementa una programación periódica de respaldos.

## Aporte de Benjamin

La rama de TI incorpora los bloques de auditoría e historial, incidentes vinculados con cuentas o eventos, administración de usuarios y permisos, configuración de días y respaldo y restauración. El cierre agrega documentación, evidencias y pruebas de integración entre roles. Los commits conservan las contribuciones anteriores de los demás integrantes.

La entrega corrige también el denominador del saldo administrativo para reflejar la política configurada por TI y valida las fechas de creación y revisión de las solicitudes persistidas. Esta validación evita aceptar datos que después no puedan mostrarse en la interfaz.

## Correspondencia con los requisitos

La siguiente matriz resume el alcance observado en el código y la demostración local. «MVP» significa implementado dentro del alcance de un expediente ficticio; no equivale a una integración institucional completa. «Parcial» indica una diferencia relevante respecto del contexto. Los identificadores corresponden al documento de contexto recibido.

| Requisito | Estado | Cobertura o diferencia principal |
| --- | --- | --- |
| RF01 Acceso y roles | Parcial | Cuatro roles y contraseña de demostración; sin autenticación institucional segura |
| RF02 Carga de archivos | MVP | PDF, imágenes y opción de cámara; cámara pendiente de prueba física |
| RF03 Metadatos | MVP | Nombre, tipo y vencimiento opcional |
| RF04 Expediente personal | MVP | Un expediente ficticio con control de titularidad |
| RF05 Corrección de documentos | MVP | Observación, corrección y conservación de versiones |
| RF06 Solicitudes | MVP | Fechas, motivo, estados y respaldo opcional |
| RF07 Pendientes administrativos | MVP | Consulta y filtro de documentos pendientes |
| RF08 Revisión documental | MVP | Aprobación o rechazo manual con observación |
| RF09 Consulta de expedientes | Parcial | Consulta del caso de demostración, sin repositorio de múltiples expedientes |
| RF10 Aprobación por Jefatura | Parcial | Jefatura resuelve solicitudes; Administrativo aprueba documentos |
| RF11 Seguimiento por centro | MVP | Jefatura consulta solicitudes del centro del caso de ejemplo |
| RF12 Gestión de RRHH | Pendiente | Sin pantalla específica de RRHH |
| RF13 Funciones de RRHH | Pendiente | Flujo de RRHH no implementado |
| RF14 Usuarios y roles | MVP | Crear, editar, activar y asignar permisos en cuatro roles |
| RF15 Auditoría | MVP | Filtros, historial y CSV de eventos disponibles |
| RF16 Incidentes | MVP | Vincular cuenta o evento, observaciones, estados y bloqueo |
| RF17 Reportes de Supervisor | Pendiente | Sin pantalla específica de Supervisor |
| RF18 Registro de actividad | Parcial | Acciones principales; faltan algunos accesos a adjuntos e intentos fallidos |
| RF19 Avisos | Parcial | Avisos dentro de la aplicación; sin notificaciones programadas de Android |
| RF20 Búsqueda | Parcial | Búsqueda y filtros locales; sin rango documental arbitrario ni consulta global |

## Requisitos no funcionales

| Requisito | Situación del MVP |
| --- | --- |
| RNF01 Control de acceso | Validaciones locales de rol, permisos, cuenta, centro y titular |
| RNF02 Protección de datos | Almacenamiento privado Android; sin cifrado propio ni comunicación remota HTTPS |
| RNF03 Inactividad | Cierre manual y revocación de sesión; falta cierre por inactividad |
| RNF04 Integridad de auditoría | Solo lectura en interfaz y conservación al restaurar; sin garantía de inmutabilidad remota |
| RNF05 Respaldos | Exportación y restauración manual con validaciones; sin programación periódica |
| RNF06 Plataforma | Android con mínimo API 26; sin versión iOS |
| RNF07 Conectividad | Operación local; falta el flujo de cargas remotas y avisos de pérdida de conexión |
| RNF08 Usabilidad | Recorridos verificados en emulador; sin estudio formal con usuarios |
| RNF09 Datos ficticios | Demostración y archivos de prueba sintéticos |

## Verificación y evidencias

La verificación reproducible se documenta en [VERIFICACION.md](VERIFICACION.md), junto con sus resultados y límites. El recorrido integrado carga un PDF, lo rechaza, corrige y aprueba, envía una solicitud, la resuelve y consulta la trazabilidad desde TI. Conserva comprobaciones de estado, versiones, archivos e identificadores además de capturas.

El selector de archivos del recorrido automático devuelve un PDF ficticio mediante una respuesta controlada. La aplicación ejecuta su importación y persistencia reales. Esta prueba no equivale a una comprobación manual de la interfaz del selector ni de la cámara. Las pruebas de respaldo verifican restauración válida, rechazo de archivos dañados y recuperación de operaciones interrumpidas.

Las capturas guardadas en `docs/evidencias` provienen de la aplicación ejecutada. Los mockups 22 a 26 se utilizaron como referencia visual para auditoría, historial, incidentes, usuarios y edición de permisos; no se presentan como evidencia de ejecución.

## Limitaciones y continuidad

Para ampliar el MVP se requiere un modelo de múltiples expedientes, identidad y autorización institucionales, almacenamiento central y sincronización. El contexto amplio también exige desarrollar RRHH y Supervisor, completar la auditoría de accesos y resolver el flujo documental de Jefatura.

Como trabajo posterior quedan notificaciones programadas, cierre por inactividad, protección de datos acorde a un despliegue institucional, respaldos periódicos y pruebas en dispositivos físicos y versiones antiguas de Android. La configuración actual corresponde a una demostración académica con datos ficticios.

## Materiales de referencia

- Documento recibido «Análisis del contexto asignado».
- «Pauta de trabajo APS 4People», usada como pauta interna del equipo, sin asumir que es una rúbrica oficial.
- Mockups 22 a 26 incluidos en el ZIP recibido.
- Código y pruebas del repositorio Proyecto APS y la rama de administración TI.

La versión editable en Word acompaña la entrega. El README contiene instrucciones para ejecutar y demostrar la aplicación. El APK se distribuye como compilación de desarrollo.
