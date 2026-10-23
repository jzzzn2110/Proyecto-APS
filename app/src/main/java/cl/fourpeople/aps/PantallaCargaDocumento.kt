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
import kotlinx.coroutines.launch

@Composable
fun PantallaCargaDocumento(
    onVolver: () -> Unit,
    onGuardado: () -> Unit
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    val formato = remember {
        DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
    }

    var nombre by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf("Contrato") }

    var fecha by rememberSaveable {
        mutableStateOf(LocalDate.now().format(formato))
    }

    var vencimiento by rememberSaveable { mutableStateOf("") }
    var horas by rememberSaveable { mutableStateOf("") }
    var archivoUri by rememberSaveable { mutableStateOf("") }
    var nombreArchivo by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    fun cancelar() {
        if (ocupado) return

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
                enabled = !ocupado
            ) {
                Text("Volver")
            }

            Text(
                "Cargar documento",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

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
                enabled = !ocupado,
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

            if (ocupado) {
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
                        enabled = !ocupado,
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
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = fecha,
                onValueChange = { fecha = it },
                label = { Text("Fecha del documento") },
                placeholder = { Text("DD/MM/AAAA") },
                singleLine = true,
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vencimiento,
                onValueChange = { vencimiento = it },
                label = { Text("Vencimiento opcional") },
                placeholder = { Text("DD/MM/AAAA") },
                singleLine = true,
                enabled = !ocupado,
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
                    enabled = !ocupado,
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
                enabled = !ocupado && archivoUri.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = {
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

                        DatosFuncionario.documentos.add(
                            0,
                            DocumentoFuncionario(
                                id = UUID.randomUUID().toString(),
                                nombre = nombre.trim(),
                                categoria = categoria,
                                fecha = fechaDocumento.format(formato),
                                vencimiento = vencimiento.trim(),
                                archivoUri = archivoUri,
                                horas = horasCurso
                            )
                        )

                        onGuardado()
                    } catch (e: Exception) {
                        error = e.message
                            ?: "Revisa los datos del documento."
                    }
                }
            ) {
                Text("Guardar documento")
            }
        }
    }
}