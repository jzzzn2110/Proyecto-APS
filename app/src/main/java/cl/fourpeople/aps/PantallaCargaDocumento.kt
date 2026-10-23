package cl.fourpeople.aps

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaCargaDocumento(
    onVolver: () -> Unit,
    onGuardado: () -> Unit,
    documentoId: String? = null
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    val formato = remember {
        DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
    }

    val original = remember(documentoId) {
        DatosFuncionario.documentos.firstOrNull { it.id == documentoId }
    }

    if (documentoId != null &&
        (original == null || original.estado != EstadoDocumento.RECHAZADO)
    ) {
        BackHandler(onBack = onVolver)
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Este documento no está disponible para corrección.")
            Button(onClick = onVolver) { Text("Volver") }
        }
        return
    }

    var nombre by rememberSaveable(documentoId) {
        mutableStateOf(original?.nombre ?: "")
    }
    var categoria by rememberSaveable(documentoId) {
        mutableStateOf(original?.categoria ?: "Contrato")
    }
    var fecha by rememberSaveable(documentoId) {
        mutableStateOf(original?.fecha ?: LocalDate.now().format(formato))
    }
    var vencimiento by rememberSaveable(documentoId) {
        mutableStateOf(original?.vencimiento ?: "")
    }
    var horas by rememberSaveable(documentoId) {
        mutableStateOf(original?.horas?.toString() ?: "")
    }

    // Solo contiene el archivo nuevo; el anterior se conserva en el historial.
    var archivoUri by rememberSaveable(documentoId) { mutableStateOf("") }
    var nombreArchivo by rememberSaveable(documentoId) { mutableStateOf("") }
    var error by rememberSaveable(documentoId) { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    var fotoPendiente by rememberSaveable(documentoId) { mutableStateOf("") }
    val bloqueado = ocupado || fotoPendiente.isNotBlank()

    fun cancelar() {
        if (bloqueado) return

        alcance.launch {
            ocupado = true

            ArchivosFuncionario.eliminarSinGuardar(
                contexto,
                archivoUri
            )

            onVolver()
        }
    }

    val selector = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { origen ->
        if (origen != null) {
            alcance.launch {
                ocupado = true
                error = null

                try {
                    val anterior = archivoUri

                    val nuevo = ArchivosFuncionario.importar(
                        contexto,
                        origen
                    )

                    archivoUri = nuevo.uri
                    nombreArchivo = nuevo.nombre

                    if (nombre.isBlank()) {
                        nombre = nuevo.nombre
                            .substringBeforeLast('.')
                            .take(120)
                    }

                    ArchivosFuncionario.eliminarSinGuardar(
                        contexto,
                        anterior
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message
                        ?: "No se pudo cargar el archivo."
                } finally {
                    ocupado = false
                }
            }
        }
    }

    val camara = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { tomada ->
        val temporal = fotoPendiente
        if (temporal.isNotBlank()) {
            ocupado = true
            alcance.launch {
                try {
                    if (tomada) {
                        val anterior = archivoUri
                        val nueva = ArchivosFuncionario.importar(
                            contexto,
                            android.net.Uri.parse(temporal)
                        )
                        archivoUri = nueva.uri
                        nombreArchivo = "Fotografía del documento"
                        if (nombre.isBlank()) {
                            nombre = "Documento fotografiado"
                        }
                        ArchivosFuncionario.eliminarSinGuardar(contexto, anterior)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo procesar la fotografía."
                } finally {
                    withContext(NonCancellable) {
                        CamaraFuncionario.eliminarTemporal(contexto, temporal)
                        fotoPendiente = ""
                        ocupado = false
                    }
                }
            }
        }
    }

    BackHandler {
        cancelar()
    }

    Scaffold(
        containerColor = Color(0xFFF5F7F4)
    ) { espacio ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(espacio)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextButton(
                onClick = { cancelar() },
                enabled = !bloqueado
            ) {
                Text("Volver")
            }

            Text(
                if (original == null) "Cargar documento" else "Corregir documento",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            if (original != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF9E1DD)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Motivo del rechazo", fontWeight = FontWeight.Bold)
                        Text(original.motivoRechazo)
                        Text(
                            "Adjunta un archivo corregido. Se enviará como versión " +
                                    "${original.version + 1}, pendiente de revisión."
                        )
                    }
                }
            }

            Text(
                "1. Adjunta un archivo",
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = {
                    selector.launch(
                        arrayOf("application/pdf", "image/*")
                    )
                },
                enabled = !bloqueado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (archivoUri.isBlank()) {
                        "Seleccionar PDF o imagen"
                    } else {
                        "Cambiar archivo"
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    error = null
                    try {
                        val destino = CamaraFuncionario.crearDestino(contexto)
                        fotoPendiente = destino.toString()
                        camara.launch(destino)
                    } catch (e: Exception) {
                        val temporal = fotoPendiente
                        fotoPendiente = ""
                        alcance.launch {
                            CamaraFuncionario.eliminarTemporal(contexto, temporal)
                        }
                        error = "No se pudo abrir la cámara. " +
                                "Comprueba que el dispositivo tenga una aplicación de cámara."
                    }
                },
                enabled = !bloqueado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tomar foto")
            }

            if (bloqueado) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp)
                )

                Text("Procesando archivo…")
            }

            if (nombreArchivo.isNotBlank()) {
                Text(nombreArchivo)
            }

            Text(
                "PDF e imágenes de hasta 20 MB.",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Text(
                "2. Categoría",
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Contrato",
                    "Anexo",
                    "Capacitación",
                    "Resolución",
                    "Decreto",
                    "Otro"
                ).forEach { opcion ->
                    FilterChip(
                        selected = categoria == opcion,
                        onClick = { categoria = opcion },
                        enabled = !bloqueado,
                        label = { Text(opcion) }
                    )
                }
            }

            Text(
                "3. Datos del documento",
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del documento") },
                singleLine = true,
                enabled = !bloqueado,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = fecha,
                onValueChange = { fecha = it },
                label = { Text("Fecha del documento") },
                placeholder = { Text("DD/MM/AAAA") },
                singleLine = true,
                enabled = !bloqueado,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vencimiento,
                onValueChange = { vencimiento = it },
                label = { Text("Vencimiento opcional") },
                placeholder = { Text("DD/MM/AAAA") },
                singleLine = true,
                enabled = !bloqueado,
                modifier = Modifier.fillMaxWidth()
            )

            if (categoria == "Capacitación") {
                OutlinedTextField(
                    value = horas,
                    onValueChange = { horas = it },
                    label = { Text("Horas de capacitación") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    enabled = !bloqueado,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                enabled = !bloqueado && archivoUri.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    if (!bloqueado) {
                        ocupado = true
                        error = null

                        alcance.launch {
                            try {
                                require(nombre.trim().length in 3..120) {
                                    "El nombre debe tener entre 3 y 120 caracteres."
                                }

                                val fechaDocumento = runCatching {
                                    LocalDate.parse(fecha.trim(), formato)
                                }.getOrElse {
                                    throw IllegalArgumentException(
                                        "Fecha inválida. Usa DD/MM/AAAA."
                                    )
                                }

                                if (vencimiento.isNotBlank()) {
                                    val fin = runCatching {
                                        LocalDate.parse(
                                            vencimiento.trim(),
                                            formato
                                        )
                                    }.getOrElse {
                                        throw IllegalArgumentException(
                                            "Vencimiento inválido. Usa DD/MM/AAAA."
                                        )
                                    }

                                    require(!fin.isBefore(fechaDocumento)) {
                                        "El vencimiento no puede ser anterior al documento."
                                    }
                                }

                                val horasCurso = if (
                                    categoria == "Capacitación"
                                ) {
                                    horas.trim().toIntOrNull()
                                } else {
                                    null
                                }

                                if (categoria == "Capacitación") {
                                    require(
                                        horasCurso != null &&
                                                horasCurso in 1..10000
                                    ) {
                                        "Indica horas entre 1 y 10000."
                                    }
                                }

                                val nuevo = if (original == null) {
                                    DocumentoFuncionario(
                                        id = UUID.randomUUID().toString(),
                                        nombre = nombre.trim(),
                                        categoria = categoria,
                                        fecha = fechaDocumento.format(formato),
                                        vencimiento = vencimiento.trim(),
                                        archivoUri = archivoUri,
                                        horas = horasCurso
                                    )
                                } else {
                                    original.copy(
                                        nombre = nombre.trim(),
                                        categoria = categoria,
                                        fecha = fechaDocumento.format(formato),
                                        estado = EstadoDocumento.PENDIENTE,
                                        vencimiento = vencimiento.trim(),
                                        motivoRechazo = "",
                                        archivoUri = archivoUri,
                                        horas = horasCurso,
                                        version = original.version + 1,
                                        historial = original.historial +
                                                original.copy(historial = emptyList())
                                    )
                                }

                                val actualizados = DatosFuncionario.documentos
                                    .toMutableList()

                                if (original == null) {
                                    actualizados.add(0, nuevo)
                                } else {
                                    val indice = actualizados.indexOfFirst {
                                        it.id == original.id
                                    }
                                    require(indice >= 0 && actualizados[indice] == original) {
                                        "El documento cambió. Vuelve a abrirlo para corregirlo."
                                    }
                                    actualizados[indice] = nuevo
                                }

                                // Completa el guardado aunque se recree la pantalla.
                                withContext(NonCancellable) {
                                    PersistenciaDocumentos.guardar(
                                        contexto.applicationContext,
                                        actualizados
                                    )

                                    DatosFuncionario.documentos.clear()
                                    DatosFuncionario.documentos.addAll(
                                        actualizados
                                    )

                                    // El archivo ya pertenece al expediente guardado.
                                    archivoUri = ""
                                }

                                onGuardado()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message
                                    ?: "No se pudo guardar el documento."
                            } finally {
                                ocupado = false
                            }
                        }
                    }
                }
            ) {
                Text(
                    if (original == null) "Guardar documento"
                    else "Enviar corrección"
                )
            }
        }
    }
}